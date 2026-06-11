package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.render.items.core.Drag;
import io.github.timer_err.qml4j.render.items.core.Flickable;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.MouseArea;
import io.github.timer_err.qml4j.render.items.core.MouseEvent;
import io.github.timer_err.qml4j.render.items.input.KeyEvent;
import io.github.timer_err.qml4j.render.items.input.Keys;
import io.github.timer_err.qml4j.render.items.input.TextEditable;
import io.github.timer_err.qml4j.render.items.input.TextInput;
import io.github.timer_err.qml4j.render.items.window.AbstractButton;

import java.util.List;

import static io.github.timer_err.qml4j.render.Renderer.zOrdered;

// Translates raw pointer/key events into item-level interactions: hit-testing,
// press/move/release capture, drag and flick scrolling, hover tracking, caret
// movement and text editing/selection, and clipboard cut/copy/paste. Reads and
// mutates active focus through the injected FocusManager.
final class EventDispatcher {

    private final FocusManager focus;
    private final Renderer renderer;
    private Item root;

    private MouseArea captured;
    private MouseArea hovered;
    private AbstractButton capturedButton;
    private float captureRootX;
    private float captureRootY;
    private Item dragTarget;
    private float dragStartX;
    private float dragStartY;
    private Flickable scrolling;
    private float scrollStartContentX;
    private float scrollStartContentY;
    // The Flickable under a captured MouseArea, eligible to steal the gesture for
    // scrolling once the drag passes DRAG_THRESHOLD (Qt's flick-steals-from-child
    // behaviour: a press on a clickable row still scrolls the list when dragged).
    private Flickable pendingFlick;
    // Drag velocity (content px/sec) tracked during a scroll, handed to the
    // Flickable on release so it coasts (fling/inertia).
    private float velX;
    private float velY;
    private long lastMoveNanos;
    private float lastMoveRootX;
    private float lastMoveRootY;

    // Pixels of content moved per mouse-wheel notch (GLFW reports ~±1 per notch).
    private static final float WHEEL_STEP = 48f;
    // Drag distance (logical px) past which a Flickable steals from a child MouseArea.
    private static final float DRAG_THRESHOLD = 10f;
    private TextEditable textCapturing;
    private Clipboard clipboard;

    EventDispatcher(FocusManager focus, Renderer renderer) {
        this.focus = focus;
        this.renderer = renderer;
    }

    void setRoot(Item root) {
        this.root = root;
    }

    void setClipboard(Clipboard cb) {
        this.clipboard = cb;
    }

    boolean copy() {
        Item f = focus.focused();
        if (!(f instanceof TextEditable)) return false;
        return copyFromSelection((TextEditable) f, false);
    }

    boolean cut() {
        Item f = focus.focused();
        if (!(f instanceof TextEditable)) return false;
        TextEditable ti = (TextEditable) f;
        if (ti.readOnly()) return false;
        return copyFromSelection(ti, true);
    }

    boolean paste() {
        Item f = focus.focused();
        if (!(f instanceof TextEditable)) return false;
        TextEditable ti = (TextEditable) f;
        if (ti.readOnly()) return false;
        if (clipboard == null) return false;
        String text = clipboard.getText();
        if (text == null || text.isEmpty()) return false;
        return applyInsert(ti, text);
    }

    private boolean copyFromSelection(TextEditable ti, boolean alsoDelete) {
        String cur = ti.text();
        if (cur == null) cur = "";
        int s = ti.selectionStart();
        int e = ti.selectionEnd();
        if (e <= s) return false;
        if (clipboard != null) clipboard.setText(cur.substring(s, e));
        if (alsoDelete) deleteSelection(ti, cur);
        return true;
    }

    boolean dispatchKey(int keyCode, String text, boolean down, boolean shift) {
        Item f = focus.focused();
        if (f != null && deliverToKeys(f, keyCode, text, down, shift)) {
            return true;
        }
        if (down && (keyCode == QmlView.KEY_TAB || keyCode == QmlView.KEY_BACKTAB)) {
            if (focus.moveFocusByTab(keyCode == QmlView.KEY_BACKTAB)) return true;
        }
        if (!(f instanceof TextEditable)) return false;
        TextEditable ti = (TextEditable) f;
        if (ti.readOnly()) return false;
        if (!down) return true;
        if (keyCode == QmlView.KEY_ENTER) {
            if (ti.handleEnter()) return true;
            return applyInsert(ti, "\n");
        }
        if (keyCode == QmlView.KEY_BACKSPACE) {
            return applyBackspace(ti);
        }
        if (keyCode == QmlView.KEY_LEFT) {
            return moveCaret(ti, -1, shift);
        }
        if (keyCode == QmlView.KEY_RIGHT) {
            return moveCaret(ti, +1, shift);
        }
        if (keyCode == QmlView.KEY_UP) {
            return moveCaretVertical(ti, -1, shift);
        }
        if (keyCode == QmlView.KEY_DOWN) {
            return moveCaretVertical(ti, +1, shift);
        }
        if (keyCode == QmlView.KEY_HOME) {
            return setCaret(ti, 0, shift);
        }
        if (keyCode == QmlView.KEY_END) {
            String cur = ti.text();
            return setCaret(ti, cur == null ? 0 : cur.length(), shift);
        }
        if (text != null && !text.isEmpty()) {
            return applyInsert(ti, text);
        }
        return false;
    }

    private boolean deliverToKeys(Item start, int keyCode, String text, boolean down, boolean shift) {
        int modifiers = shift ? KeyEvent.SHIFT : 0;
        KeyEvent event = new KeyEvent(keyCode, text, modifiers);
        for (Item it = start; it != null; it = it.parent.peek()) {
            Keys keys = it.keysOrNull();
            if (keys == null) continue;
            (down ? keys.pressed : keys.released).emit(event);
            if (down) {
                Signal specific = specificKeysSignal(keys, keyCode, text);
                if (specific != null) specific.emit(event);
            }
            if (event.accepted) return true;
        }
        return false;
    }

    private static Signal specificKeysSignal(Keys keys, int keyCode, String text) {
        switch (keyCode) {
            case QmlView.KEY_ENTER: return keys.returnPressed;
            case QmlView.KEY_ESCAPE: return keys.escapePressed;
            case QmlView.KEY_TAB: return keys.tabPressed;
            case QmlView.KEY_BACKTAB: return keys.backtabPressed;
            case QmlView.KEY_BACKSPACE: return keys.backPressed;
            case QmlView.KEY_UP: return keys.upPressed;
            case QmlView.KEY_DOWN: return keys.downPressed;
            case QmlView.KEY_LEFT: return keys.leftPressed;
            case QmlView.KEY_RIGHT: return keys.rightPressed;
            default:
                return " ".equals(text) ? keys.spacePressed : null;
        }
    }

    private static boolean moveCaret(TextEditable ti, int delta, boolean shift) {
        String cur = ti.text();
        int len = cur == null ? 0 : cur.length();
        int pos = clampPos(ti.cursorPosition(), len);
        int next = clampPos(pos + delta, len);
        if (next == pos) return false;
        return setCaret(ti, next, shift);
    }

    private boolean moveCaretVertical(TextEditable ti, int delta, boolean shift) {
        String cur = ti.text();
        int len = cur == null ? 0 : cur.length();
        int pos = clampPos(ti.cursorPosition(), len);
        int next = clampPos(ti.moveCaretVertical(pos, delta, renderer), len);
        if (next == pos) return false;
        return setCaret(ti, next, shift);
    }

    private static boolean setCaret(TextEditable ti, int pos, boolean shift) {
        String cur = ti.text();
        int len = cur == null ? 0 : cur.length();
        int target = clampPos(pos, len);
        if (shift) {
            if (ti.selectionAnchor() < 0) {
                ti.setSelectionAnchor(clampPos(ti.cursorPosition(), len));
            }
            int anchor = ti.selectionAnchor();
            setSelection(ti, Math.min(anchor, target), Math.max(anchor, target));
        } else {
            clearSelection(ti);
        }
        ti.setCursorPosition(target);
        return true;
    }

    private static boolean applyBackspace(TextEditable ti) {
        String cur = ti.text();
        if (cur == null) cur = "";
        if (deleteSelection(ti, cur)) return true;
        int pos = clampPos(ti.cursorPosition(), cur.length());
        if (pos == 0) return false;
        String next = cur.substring(0, pos - 1) + cur.substring(pos);
        ti.setText(next);
        ti.setCursorPosition(pos - 1);
        ti.emitTextChanged();
        return true;
    }

    private static boolean applyInsert(TextEditable ti, String text) {
        String cur = ti.text();
        if (cur == null) cur = "";
        int selS = ti.selectionStart();
        int selE = ti.selectionEnd();
        boolean hasSel = selE > selS;
        int caretBase = hasSel ? selS : clampPos(ti.cursorPosition(), cur.length());
        int reservedLen = hasSel ? cur.length() - (selE - selS) : cur.length();
        int max = ti.maximumLength();
        int room = Math.max(0, max - reservedLen);
        if (room == 0 && !hasSel) return false;
        String add = text.length() > room ? text.substring(0, room) : text;
        String head = cur.substring(0, hasSel ? selS : caretBase);
        String tail = cur.substring(hasSel ? selE : caretBase);
        ti.setText(head + add + tail);
        ti.setCursorPosition(caretBase + add.length());
        clearSelection(ti);
        ti.emitTextChanged();
        return true;
    }

    private static boolean deleteSelection(TextEditable ti, String cur) {
        int s = ti.selectionStart();
        int e = ti.selectionEnd();
        if (e <= s) return false;
        ti.setText(cur.substring(0, s) + cur.substring(e));
        ti.setCursorPosition(s);
        clearSelection(ti);
        ti.emitTextChanged();
        return true;
    }

    private static void setSelection(TextEditable ti, int start, int end) {
        ti.setSelectionRange(start, end);
    }

    private static void clearSelection(TextEditable ti) {
        ti.setSelectionAnchor(-1);
        ti.setSelectionRange(0, 0);
    }

    private static int clampPos(int p, int len) {
        if (p < 0) return 0;
        if (p > len) return len;
        return p;
    }

    boolean dispatchClick(float x, float y) {
        return dispatchPointerDown(x, y) && dispatchPointerUp(x, y);
    }

    boolean dispatchPointerDown(float x, float y) {
        if (root == null) return false;
        TextEditable ti = hitTestTextEditable(root, x, y);
        if (ti != null) {
            focus.setFocus((Item) ti);
            float[] local = localCoords((Item) ti, x, y);
            int idx = ti.caretIndexAt(local[0], local[1], renderer);
            clearSelection(ti);
            ti.setSelectionAnchor(idx);
            ti.setCursorPosition(idx);
            textCapturing = ti;
            return true;
        }
        if (focus.focused() instanceof TextEditable) focus.clearFocus();
        AbstractButton btn = hitTestButton(root, x, y);
        if (btn != null) {
            capturedButton = btn;
            captureRootX = x;
            captureRootY = y;
            if (!Boolean.FALSE.equals(btn.enabled.peek())) btn.press();
            return true;
        }
        MouseArea hit = hitTestMouseArea(root, x, y);
        if (hit != null) {
            captured = hit;
            captureRootX = x;
            captureRootY = y;
            float[] local = localCoords(hit, x, y);
            hit.mouseX.set(local[0]);
            hit.mouseY.set(local[1]);
            hit.pressed.set(Boolean.TRUE);
            setContains(hit, true);
            hit.pressedSignal.emit(new MouseEvent(local[0], local[1]));
            beginDragIfRequested(hit);
            // If the MouseArea isn't dragging its own target, remember the
            // Flickable beneath it so a drag past threshold scrolls the list.
            pendingFlick = dragTarget == null ? hitTestFlickable(root, x, y) : null;
            if (pendingFlick != null) {
                pendingFlick.stopScroll();
                scrollStartContentX = pendingFlick.contentX.peekFloat();
                scrollStartContentY = pendingFlick.contentY.peekFloat();
            }
            return true;
        }
        Flickable f = hitTestFlickable(root, x, y);
        if (f == null) return false;
        f.stopScroll();
        scrolling = f;
        f.moving.set(Boolean.TRUE);
        captureRootX = x;
        captureRootY = y;
        scrollStartContentX = f.contentX.peekFloat();
        scrollStartContentY = f.contentY.peekFloat();
        beginScrollVelocity(x, y);
        return true;
    }

    boolean dispatchPointerMove(float x, float y) {
        if (textCapturing != null) {
            extendTextSelection(x, y);
            return true;
        }
        if (captured != null) {
            if (pendingFlick != null && dragTarget == null && stealsToFlick(x, y)) {
                return true;
            }
            float[] local = localCoords(captured, x, y);
            captured.mouseX.set(local[0]);
            captured.mouseY.set(local[1]);
            setContains(captured, within(captured, local));
            applyDrag(x, y);
            captured.positionChanged.emit(new MouseEvent(local[0], local[1]));
            return true;
        }
        if (scrolling != null) {
            applyScroll(x, y);
            return true;
        }
        return updateHover(x, y);
    }

    private boolean updateHover(float x, float y) {
        MouseArea hit = hitTestMouseArea(root, x, y);
        MouseArea next = (hit != null && Boolean.TRUE.equals(hit.hoverEnabled.peek())) ? hit : null;
        if (next == hovered) return next != null;
        if (hovered != null) setContains(hovered, false);
        hovered = next;
        if (hovered != null) setContains(hovered, true);
        return hovered != null;
    }

    // Toggles containsMouse and fires entered/exited only on a real transition.
    private static void setContains(MouseArea ma, boolean inside) {
        if (Boolean.valueOf(inside).equals(ma.containsMouse.peek())) return;
        ma.containsMouse.set(inside);
        (inside ? ma.entered : ma.exited).emit();
    }

    private static boolean within(MouseArea ma, float[] local) {
        return local[0] >= 0 && local[1] >= 0
            && local[0] <= ma.width.peekFloat()
            && local[1] <= ma.height.peekFloat();
    }

    boolean dispatchPointerUp(float x, float y) {
        if (textCapturing != null) {
            extendTextSelection(x, y);
            textCapturing = null;
            return true;
        }
        if (capturedButton != null) {
            AbstractButton b = capturedButton;
            capturedButton = null;
            if (Boolean.FALSE.equals(b.enabled.peek())) return true;
            float[] local = localCoords(b, x, y);
            boolean inside = local[0] >= 0 && local[1] >= 0
                && local[0] <= b.width.peekFloat()
                && local[1] <= b.height.peekFloat();
            if (inside) b.releaseInside(); else b.releaseOutside();
            return true;
        }
        if (captured != null) {
            pendingFlick = null;
            MouseArea target = captured;
            float[] local = localCoords(target, x, y);
            target.mouseX.set(local[0]);
            target.mouseY.set(local[1]);
            target.pressed.set(Boolean.FALSE);
            setContains(target, false);
            endDrag(target);
            target.released.emit(new MouseEvent(local[0], local[1]));
            boolean inside = local[0] >= 0 && local[1] >= 0
                && local[0] <= target.width.peekFloat()
                && local[1] <= target.height.peekFloat();
            if (inside) target.clicked.emit(new MouseEvent(local[0], local[1]));
            captured = null;
            return true;
        }
        if (scrolling != null) {
            applyScroll(x, y);
            scrolling.startFling(velX, velY);
            scrolling = null;
            return true;
        }
        return false;
    }

    private void extendTextSelection(float x, float y) {
        TextEditable ti = textCapturing;
        float[] local = localCoords((Item) ti, x, y);
        int idx = ti.caretIndexAt(local[0], local[1], renderer);
        if (ti.selectionAnchor() < 0) ti.setSelectionAnchor(idx);
        int anchor = ti.selectionAnchor();
        int s = Math.min(anchor, idx);
        int e = Math.max(anchor, idx);
        setSelection(ti, s, e);
        ti.setCursorPosition(idx);
    }

    private void beginDragIfRequested(MouseArea hit) {
        Item dt = hit.drag.target.peek();
        if (dt == null) return;
        dragTarget = dt;
        dragStartX = dt.x.peekFloat();
        dragStartY = dt.y.peekFloat();
    }

    private void applyDrag(float rootX, float rootY) {
        if (dragTarget == null) return;
        Drag drag = captured.drag;
        String axis = drag.axis.peek();
        boolean allowX = !"YAxis".equals(axis);
        boolean allowY = !"XAxis".equals(axis);
        float dx = rootX - captureRootX;
        float dy = rootY - captureRootY;
        if (allowX) {
            float nx = clamp(dragStartX + dx,
                             drag.minimumX.peekFloat(),
                             drag.maximumX.peekFloat());
            dragTarget.x.set(nx);
        }
        if (allowY) {
            float ny = clamp(dragStartY + dy,
                             drag.minimumY.peekFloat(),
                             drag.maximumY.peekFloat());
            dragTarget.y.set(ny);
        }
        drag.active.set(Boolean.TRUE);
    }

    private void endDrag(MouseArea hit) {
        if (dragTarget == null) return;
        hit.drag.active.set(Boolean.FALSE);
        dragTarget = null;
    }

    // True once a captured MouseArea's drag passes the threshold along the
    // Flickable's scroll axis: cancels the MouseArea press (no click) and hands
    // the live gesture to the Flickable, so list rows stay tappable yet scroll.
    private boolean stealsToFlick(float x, float y) {
        String dir = pendingFlick.flickableDirection.peek();
        boolean allowX = !"VerticalFlick".equals(dir);
        boolean allowY = !"HorizontalFlick".equals(dir);
        boolean past = (allowY && Math.abs(y - captureRootY) > DRAG_THRESHOLD)
                    || (allowX && Math.abs(x - captureRootX) > DRAG_THRESHOLD);
        if (!past) return false;
        MouseArea ma = captured;
        ma.pressed.set(Boolean.FALSE);
        setContains(ma, false);
        captured = null;
        scrolling = pendingFlick;
        pendingFlick = null;
        scrolling.moving.set(Boolean.TRUE);
        captureRootX = x;
        captureRootY = y;
        scrollStartContentX = scrolling.contentX.peekFloat();
        scrollStartContentY = scrolling.contentY.peekFloat();
        beginScrollVelocity(x, y);
        applyScroll(x, y);
        return true;
    }

    // Begin (or restart) velocity tracking for a fresh drag at (x, y).
    private void beginScrollVelocity(float x, float y) {
        velX = 0f;
        velY = 0f;
        lastMoveRootX = x;
        lastMoveRootY = y;
        lastMoveNanos = System.nanoTime();
    }

    // Update the running drag velocity from a move. Content moves opposite the
    // finger, so content velocity = -(finger delta)/dt; smoothed to ride out jitter.
    private void trackScrollVelocity(float x, float y) {
        long now = System.nanoTime();
        float dt = (now - lastMoveNanos) / 1_000_000_000f;
        if (dt > 0.001f) {
            float ivx = -(x - lastMoveRootX) / dt;
            float ivy = -(y - lastMoveRootY) / dt;
            velX = 0.4f * velX + 0.6f * ivx;
            velY = 0.4f * velY + 0.6f * ivy;
            lastMoveRootX = x;
            lastMoveRootY = y;
            lastMoveNanos = now;
        }
    }

    private void applyScroll(float rootX, float rootY) {
        trackScrollVelocity(rootX, rootY);
        Flickable f = scrolling;
        String dir = f.flickableDirection.peek();
        boolean allowX = !"VerticalFlick".equals(dir);
        boolean allowY = !"HorizontalFlick".equals(dir);
        float w = f.width.peekFloat();
        float h = f.height.peekFloat();
        float cw = f.contentWidth.peekFloat();
        float ch = f.contentHeight.peekFloat();
        // Flickable.bottomMargin/rightMargin extend the scrollable range past the content
        // so trailing content (e.g. a page's last row under a 32px bottom margin) can be
        // brought fully into view.
        float maxX = Math.max(0f, cw + f.rightMargin.peekFloat() - w);
        float maxY = Math.max(0f, ch + f.bottomMargin.peekFloat() - h);
        // Drag tracks the finger 1:1 (responsive); the eased glide is only the
        // release inertia. Keep the fling target in step via syncTarget.
        if (allowX) {
            f.contentX.set(clamp(scrollStartContentX - (rootX - captureRootX), 0f, maxX));
        }
        if (allowY) {
            f.contentY.set(clamp(scrollStartContentY - (rootY - captureRootY), 0f, maxY));
        }
        f.syncTarget();
    }

    // Mouse wheel over the innermost interactive Flickable at (x, y): dx/dy are wheel
    // offsets (GLFW convention, +y = scroll up). A vertical wheel scrolls Y, or X when
    // the Flickable only scrolls horizontally (wheel over a horizontal list).
    boolean dispatchWheel(float x, float y, float dx, float dy) {
        Flickable f = hitTestFlickable(root, x, y);
        if (f == null) return false;
        String dir = f.flickableDirection.peek();
        boolean allowX = !"VerticalFlick".equals(dir);
        boolean allowY = !"HorizontalFlick".equals(dir);
        float maxX = Math.max(0f, f.contentWidth.peekFloat() + f.rightMargin.peekFloat() - f.width.peekFloat());
        float maxY = Math.max(0f, f.contentHeight.peekFloat() + f.bottomMargin.peekFloat() - f.height.peekFloat());
        boolean scrolled = false;
        float vy = -dy * WHEEL_STEP;
        if (allowY && maxY > 0f) {
            f.contentY.set(clamp(f.contentY.peekFloat() + vy, 0f, maxY));
            scrolled = true;
        } else if (allowX && maxX > 0f) {
            f.contentX.set(clamp(f.contentX.peekFloat() + vy, 0f, maxX));
            scrolled = true;
        }
        if (allowX && maxX > 0f && dx != 0f) {
            f.contentX.set(clamp(f.contentX.peekFloat() - dx * WHEEL_STEP, 0f, maxX));
            scrolled = true;
        }
        if (scrolled) f.syncTarget();
        return scrolled;
    }

    private Flickable hitTestFlickable(Item item, float x, float y) {
        if (!item.isVisible()) return null;
        float ix = item.x.peekFloat();
        float iy = item.y.peekFloat();
        float w = item.width.peekFloat();
        float h = item.height.peekFloat();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return null;
        float childLx = lx;
        float childLy = ly;
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            childLx += f.contentX.peekFloat();
            childLy += f.contentY.peekFloat();
        }
        List<Item> ordered = zOrdered(item.children);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Flickable hit = hitTestFlickable(ordered.get(i), childLx, childLy);
            if (hit != null) return hit;
        }
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            if (Boolean.TRUE.equals(f.interactive.peek())) return f;
        }
        return null;
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    TextEditable pickTextEditable(float x, float y) {
        return root == null ? null : hitTestTextEditable(root, x, y);
    }

    TextInput pickTextInput(float x, float y) {
        TextEditable te = pickTextEditable(x, y);
        return te instanceof TextInput ? (TextInput) te : null;
    }

    private TextEditable hitTestTextEditable(Item item, float x, float y) {
        if (!item.isVisible()) return null;
        float ix = item.x.peekFloat();
        float iy = item.y.peekFloat();
        float w = item.width.peekFloat();
        float h = item.height.peekFloat();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return null;
        float childLx = lx;
        float childLy = ly;
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            childLx += f.contentX.peekFloat();
            childLy += f.contentY.peekFloat();
        }
        List<Item> ordered = zOrdered(item.children);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            TextEditable hit = hitTestTextEditable(ordered.get(i), childLx, childLy);
            if (hit != null) return hit;
        }
        return item instanceof TextEditable ? (TextEditable) item : null;
    }

    private AbstractButton hitTestButton(Item item, float x, float y) {
        if (!item.isVisible()) return null;
        float ix = item.x.peekFloat();
        float iy = item.y.peekFloat();
        float w = item.width.peekFloat();
        float h = item.height.peekFloat();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return null;
        float childLx = lx;
        float childLy = ly;
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            childLx += f.contentX.peekFloat();
            childLy += f.contentY.peekFloat();
        }
        List<Item> ordered = zOrdered(item.children);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            AbstractButton hit = hitTestButton(ordered.get(i), childLx, childLy);
            if (hit != null) return hit;
        }
        return item instanceof AbstractButton ? (AbstractButton) item : null;
    }

    private MouseArea hitTestMouseArea(Item item, float x, float y) {
        if (!item.isVisible()) return null;
        float ix = item.x.peekFloat();
        float iy = item.y.peekFloat();
        float w = item.width.peekFloat();
        float h = item.height.peekFloat();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return null;
        float childLx = lx;
        float childLy = ly;
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            childLx += f.contentX.peekFloat();
            childLy += f.contentY.peekFloat();
        }
        List<Item> ordered = zOrdered(item.children);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MouseArea hit = hitTestMouseArea(ordered.get(i), childLx, childLy);
            if (hit != null) return hit;
        }
        // A MouseArea is transparent to presses when disabled or accepting no
        // buttons (Qt: acceptedButtons: Qt.NoButton) — let the press fall through.
        if (item instanceof MouseArea) {
            MouseArea ma = (MouseArea) item;
            if (Boolean.FALSE.equals(ma.enabled.peek())) return null;
            if (ma.acceptedButtons.peekInt() == 0) return null;
            return ma;
        }
        return null;
    }

    private float[] localCoords(Item target, float rootX, float rootY) {
        float ox = 0, oy = 0;
        Item cur = target;
        while (cur != null) {
            ox += cur.x.peekFloat();
            oy += cur.y.peekFloat();
            Item p = cur.parent.peek();
            if (p instanceof Flickable) {
                Flickable f = (Flickable) p;
                ox -= f.contentX.peekFloat();
                oy -= f.contentY.peekFloat();
            }
            cur = p;
        }
        return new float[]{rootX - ox, rootY - oy};
    }
}
