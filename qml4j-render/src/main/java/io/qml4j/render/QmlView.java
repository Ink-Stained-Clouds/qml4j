package io.qml4j.render;

import io.qml4j.render.items.Animatable;
import io.qml4j.render.items.Drag;
import io.qml4j.render.items.Flickable;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.NumberAnimation;
import io.qml4j.render.items.TextInput;

import io.github.humbleui.skija.Canvas;
import io.qml4j.compiler.CompiledUnit;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.compiler.bytecode.QmlCompiler;
import io.qml4j.engine.QObject;
import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.classloader.ClassLoaderBackend;
import io.qml4j.parser.Qml4j;
import io.qml4j.parser.ast.Ast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.qml4j.render.Renderer.zOrdered;

public final class QmlView {

    private final QmlEngine engine;
    private final TypeRegistry types;
    private final QmlCompiler compiler = new QmlCompiler();
    private final Renderer renderer = new Renderer();
    private final DirtyQueue dirty = new DirtyQueue();
    private final Map<String, Class<? extends QObject>> importedTypes = new HashMap<>();
    private final Set<String> compilingNow = new HashSet<>();
    private ResourceLoader resources;
    private Item root;

    public QmlView(QmlEngine engine, TypeRegistry types) {
        this.engine = engine;
        this.types = types;
        renderer.setComponentFactory(this::instantiate);
    }

    public static QmlView withStockTypes(QmlEngine engine) {
        return new QmlView(engine, StockTypes.registry());
    }

    public QmlView resources(ResourceLoader loader) {
        this.resources = loader;
        renderer.setResourceLoader(loader);
        return this;
    }

    public Item load(String qml) {
        root = instantiate(qml);
        initialFocusScan(root);
        return root;
    }

    private void initialFocusScan(Item node) {
        if (node == null) return;
        if (Boolean.TRUE.equals(node.focus.peek())) {
            setFocus(node);
            return;
        }
        for (Item c : node.children) {
            initialFocusScan(c);
            if (focused != null) return;
        }
    }

    private Item instantiate(String qml) {
        Ast.QmlDocument doc = Qml4j.parse(qml);
        Class<? extends QObject> rootClass = compileAndDefine(doc);
        Object inst;
        try {
            inst = rootClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        if (!(inst instanceof Item)) {
            throw new IllegalArgumentException(
                "root QML type must extend Item, got " + inst.getClass().getName());
        }
        return (Item) inst;
    }

    private Class<? extends QObject> compileAndDefine(Ast.QmlDocument doc) {
        List<String> prefixes = stringImportPrefixes(doc);
        TypeRegistry docTypes = types.copy().withResolver(name -> resolveCompound(name, prefixes));
        CompiledUnit unit = compiler.compile(doc, docTypes);
        ClassLoaderBackend backend = engine.backend();
        Map<String, Class<?>> defined = backend.defineClasses(unit.classes());
        Class<?> rootClass = defined.get(unit.rootClassName());
        if (rootClass == null) {
            throw new IllegalStateException("compiled unit missing root class");
        }
        if (!QObject.class.isAssignableFrom(rootClass)) {
            throw new IllegalStateException(
                "compiled root class is not a QObject: " + rootClass.getName());
        }
        @SuppressWarnings("unchecked")
        Class<? extends QObject> qc = (Class<? extends QObject>) rootClass;
        return qc;
    }

    private Class<? extends QObject> resolveCompound(String name, List<String> prefixes) {
        Class<? extends QObject> cached = importedTypes.get(name);
        if (cached != null) return cached;
        if (resources == null) return null;
        for (String p : prefixes) {
            String path = p.isEmpty() ? name + ".qml" : p + "/" + name + ".qml";
            byte[] bytes = resources.load(path);
            if (bytes == null) continue;
            if (!compilingNow.add(name)) {
                throw new IllegalStateException("cyclic import: " + name);
            }
            try {
                Ast.QmlDocument subDoc = Qml4j.parse(new String(bytes, StandardCharsets.UTF_8));
                Class<? extends QObject> rootClass = compileAndDefine(subDoc);
                importedTypes.put(name, rootClass);
                return rootClass;
            } finally {
                compilingNow.remove(name);
            }
        }
        return null;
    }

    private static List<String> stringImportPrefixes(Ast.QmlDocument doc) {
        List<String> out = new ArrayList<>();
        for (Ast.ImportNode imp : doc.imports) {
            if (!imp.isStringPath) continue;
            String p = imp.moduleOrPath;
            if (p == null) continue;
            if (".".equals(p)) out.add("");
            else if (p.startsWith("./")) out.add(p.substring(2));
            else out.add(p);
        }
        return out;
    }

    public Item root() {
        return root;
    }

    private MouseArea captured;
    private float captureRootX;
    private float captureRootY;
    private Item dragTarget;
    private float dragStartX;
    private float dragStartY;
    private Flickable scrolling;
    private float scrollStartContentX;
    private float scrollStartContentY;
    private Item focused;
    private FocusListener focusListener;
    private TextInput textCapturing;

    public interface FocusListener {
        void onFocusChanged(Item newFocus, Item oldFocus);
    }

    public void setFocusListener(FocusListener l) {
        this.focusListener = l;
    }

    public Item focused() {
        return focused;
    }

    public void setFocus(Item it) {
        if (focused == it) return;
        Item old = focused;
        if (old != null) {
            old.activeFocus.set(Boolean.FALSE);
            old.focus.set(Boolean.FALSE);
            if (old instanceof TextInput) clearSelection((TextInput) old);
        }
        focused = it;
        if (it != null) {
            it.focus.set(Boolean.TRUE);
            it.activeFocus.set(Boolean.TRUE);
        }
        if (focusListener != null) focusListener.onFocusChanged(it, old);
    }

    public void clearFocus() {
        setFocus(null);
    }

    public boolean dispatchKey(int keyCode, String text, boolean down) {
        return dispatchKey(keyCode, text, down, false);
    }

    public boolean dispatchKey(int keyCode, String text, boolean down, boolean shift) {
        if (!(focused instanceof TextInput)) return false;
        TextInput ti = (TextInput) focused;
        if (Boolean.TRUE.equals(ti.readOnly.peek())) return false;
        if (!down) return true;
        if (keyCode == KEY_ENTER) {
            ti.accepted.emit();
            return true;
        }
        if (keyCode == KEY_BACKSPACE) {
            return applyBackspace(ti);
        }
        if (keyCode == KEY_LEFT) {
            return moveCaret(ti, -1, shift);
        }
        if (keyCode == KEY_RIGHT) {
            return moveCaret(ti, +1, shift);
        }
        if (keyCode == KEY_HOME) {
            return setCaret(ti, 0, shift);
        }
        if (keyCode == KEY_END) {
            String cur = ti.text.peek();
            return setCaret(ti, cur == null ? 0 : cur.length(), shift);
        }
        if (text != null && !text.isEmpty()) {
            return applyInsert(ti, text);
        }
        return false;
    }

    public static final int KEY_BACKSPACE = -1;
    public static final int KEY_ENTER = -2;
    public static final int KEY_LEFT = -3;
    public static final int KEY_RIGHT = -4;
    public static final int KEY_HOME = -5;
    public static final int KEY_END = -6;

    private static boolean moveCaret(TextInput ti, int delta, boolean shift) {
        String cur = ti.text.peek();
        int len = cur == null ? 0 : cur.length();
        int pos = clampPos(ti.cursorPosition.peek().intValue(), len);
        int next = clampPos(pos + delta, len);
        if (next == pos) return false;
        return setCaret(ti, next, shift);
    }

    private static boolean setCaret(TextInput ti, int pos, boolean shift) {
        String cur = ti.text.peek();
        int len = cur == null ? 0 : cur.length();
        int target = clampPos(pos, len);
        if (shift) {
            if (ti.selectionAnchor < 0) {
                ti.selectionAnchor = clampPos(ti.cursorPosition.peek().intValue(), len);
            }
            setSelection(ti, Math.min(ti.selectionAnchor, target), Math.max(ti.selectionAnchor, target));
        } else {
            clearSelection(ti);
        }
        ti.cursorPosition.set(target);
        return true;
    }

    private static boolean applyBackspace(TextInput ti) {
        String cur = ti.text.peek();
        if (cur == null) cur = "";
        if (deleteSelection(ti, cur)) return true;
        int pos = clampPos(ti.cursorPosition.peek().intValue(), cur.length());
        if (pos == 0) return false;
        String next = cur.substring(0, pos - 1) + cur.substring(pos);
        ti.text.set(next);
        ti.cursorPosition.set(pos - 1);
        ti.textChanged.emit();
        return true;
    }

    private static boolean applyInsert(TextInput ti, String text) {
        String cur = ti.text.peek();
        if (cur == null) cur = "";
        int selS = ti.selectionStart.peek().intValue();
        int selE = ti.selectionEnd.peek().intValue();
        boolean hasSel = selE > selS;
        int caretBase = hasSel ? selS : clampPos(ti.cursorPosition.peek().intValue(), cur.length());
        int reservedLen = hasSel ? cur.length() - (selE - selS) : cur.length();
        int max = ti.maximumLength.peek().intValue();
        int room = Math.max(0, max - reservedLen);
        if (room == 0 && !hasSel) return false;
        String add = text.length() > room ? text.substring(0, room) : text;
        String head = cur.substring(0, hasSel ? selS : caretBase);
        String tail = cur.substring(hasSel ? selE : caretBase);
        ti.text.set(head + add + tail);
        ti.cursorPosition.set(caretBase + add.length());
        clearSelection(ti);
        ti.textChanged.emit();
        return true;
    }

    private static boolean deleteSelection(TextInput ti, String cur) {
        int s = ti.selectionStart.peek().intValue();
        int e = ti.selectionEnd.peek().intValue();
        if (e <= s) return false;
        ti.text.set(cur.substring(0, s) + cur.substring(e));
        ti.cursorPosition.set(s);
        clearSelection(ti);
        ti.textChanged.emit();
        return true;
    }

    private static void setSelection(TextInput ti, int start, int end) {
        ti.selectionStart.set(start);
        ti.selectionEnd.set(end);
    }

    private static void clearSelection(TextInput ti) {
        ti.selectionAnchor = -1;
        ti.selectionStart.set(0);
        ti.selectionEnd.set(0);
    }

    private static int clampPos(int p, int len) {
        if (p < 0) return 0;
        if (p > len) return len;
        return p;
    }

    public boolean dispatchClick(float x, float y) {
        return dispatchPointerDown(x, y) && dispatchPointerUp(x, y);
    }

    public boolean dispatchPointerDown(float x, float y) {
        if (root == null) return false;
        TextInput ti = hitTestTextInput(root, x, y);
        if (ti != null) {
            setFocus(ti);
            float[] local = localCoords(ti, x, y);
            int idx = renderer.caretIndexFor(ti, local[0]);
            clearSelection(ti);
            ti.selectionAnchor = idx;
            ti.cursorPosition.set(idx);
            textCapturing = ti;
            return true;
        }
        if (focused != null) clearFocus();
        MouseArea hit = hitTestMouseArea(root, x, y);
        if (hit != null) {
            captured = hit;
            captureRootX = x;
            captureRootY = y;
            float[] local = localCoords(hit, x, y);
            hit.mouseX.set(local[0]);
            hit.mouseY.set(local[1]);
            hit.isPressed.set(Boolean.TRUE);
            hit.pressed.emit();
            beginDragIfRequested(hit);
            return true;
        }
        Flickable f = hitTestFlickable(root, x, y);
        if (f == null) return false;
        scrolling = f;
        captureRootX = x;
        captureRootY = y;
        scrollStartContentX = f.contentX.peek().floatValue();
        scrollStartContentY = f.contentY.peek().floatValue();
        return true;
    }

    public boolean dispatchPointerMove(float x, float y) {
        if (textCapturing != null) {
            extendTextSelection(x, y);
            return true;
        }
        if (captured != null) {
            float[] local = localCoords(captured, x, y);
            captured.mouseX.set(local[0]);
            captured.mouseY.set(local[1]);
            applyDrag(x, y);
            captured.positionChanged.emit();
            return true;
        }
        if (scrolling != null) {
            applyScroll(x, y);
            return true;
        }
        return false;
    }

    public boolean dispatchPointerUp(float x, float y) {
        if (textCapturing != null) {
            extendTextSelection(x, y);
            textCapturing = null;
            return true;
        }
        if (captured != null) {
            MouseArea target = captured;
            float[] local = localCoords(target, x, y);
            target.mouseX.set(local[0]);
            target.mouseY.set(local[1]);
            target.isPressed.set(Boolean.FALSE);
            endDrag(target);
            target.released.emit();
            boolean inside = local[0] >= 0 && local[1] >= 0
                && local[0] <= target.width.peek().floatValue()
                && local[1] <= target.height.peek().floatValue();
            if (inside) target.clicked.emit();
            captured = null;
            return true;
        }
        if (scrolling != null) {
            applyScroll(x, y);
            scrolling = null;
            return true;
        }
        return false;
    }

    private void extendTextSelection(float x, float y) {
        TextInput ti = textCapturing;
        float[] local = localCoords(ti, x, y);
        int idx = renderer.caretIndexFor(ti, local[0]);
        if (ti.selectionAnchor < 0) ti.selectionAnchor = idx;
        int s = Math.min(ti.selectionAnchor, idx);
        int e = Math.max(ti.selectionAnchor, idx);
        setSelection(ti, s, e);
        ti.cursorPosition.set(idx);
    }

    private void beginDragIfRequested(MouseArea hit) {
        Item dt = hit.drag.target.peek();
        if (dt == null) return;
        dragTarget = dt;
        dragStartX = dt.x.peek().floatValue();
        dragStartY = dt.y.peek().floatValue();
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
                             drag.minimumX.peek().floatValue(),
                             drag.maximumX.peek().floatValue());
            dragTarget.x.set(nx);
        }
        if (allowY) {
            float ny = clamp(dragStartY + dy,
                             drag.minimumY.peek().floatValue(),
                             drag.maximumY.peek().floatValue());
            dragTarget.y.set(ny);
        }
        drag.active.set(Boolean.TRUE);
    }

    private void endDrag(MouseArea hit) {
        if (dragTarget == null) return;
        hit.drag.active.set(Boolean.FALSE);
        dragTarget = null;
    }

    private void applyScroll(float rootX, float rootY) {
        Flickable f = scrolling;
        String dir = f.flickableDirection.peek();
        boolean allowX = !"VerticalFlick".equals(dir);
        boolean allowY = !"HorizontalFlick".equals(dir);
        float w = f.width.peek().floatValue();
        float h = f.height.peek().floatValue();
        float cw = f.contentWidth.peek().floatValue();
        float ch = f.contentHeight.peek().floatValue();
        float maxX = Math.max(0f, cw - w);
        float maxY = Math.max(0f, ch - h);
        if (allowX) {
            float nx = clamp(scrollStartContentX - (rootX - captureRootX), 0f, maxX);
            f.contentX.set(nx);
        }
        if (allowY) {
            float ny = clamp(scrollStartContentY - (rootY - captureRootY), 0f, maxY);
            f.contentY.set(ny);
        }
    }

    private Flickable hitTestFlickable(Item item, float x, float y) {
        if (!item.visible.peek()) return null;
        float ix = item.x.peek().floatValue();
        float iy = item.y.peek().floatValue();
        float w = item.width.peek().floatValue();
        float h = item.height.peek().floatValue();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return null;
        float childLx = lx;
        float childLy = ly;
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            childLx += f.contentX.peek().floatValue();
            childLy += f.contentY.peek().floatValue();
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

    public TextInput pickTextInput(float x, float y) {
        return root == null ? null : hitTestTextInput(root, x, y);
    }

    private TextInput hitTestTextInput(Item item, float x, float y) {
        if (!item.visible.peek()) return null;
        float ix = item.x.peek().floatValue();
        float iy = item.y.peek().floatValue();
        float w = item.width.peek().floatValue();
        float h = item.height.peek().floatValue();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return null;
        float childLx = lx;
        float childLy = ly;
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            childLx += f.contentX.peek().floatValue();
            childLy += f.contentY.peek().floatValue();
        }
        List<Item> ordered = zOrdered(item.children);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            TextInput hit = hitTestTextInput(ordered.get(i), childLx, childLy);
            if (hit != null) return hit;
        }
        return item instanceof TextInput ? (TextInput) item : null;
    }

    private MouseArea hitTestMouseArea(Item item, float x, float y) {
        if (!item.visible.peek()) return null;
        float ix = item.x.peek().floatValue();
        float iy = item.y.peek().floatValue();
        float w = item.width.peek().floatValue();
        float h = item.height.peek().floatValue();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return null;
        float childLx = lx;
        float childLy = ly;
        if (item instanceof Flickable) {
            Flickable f = (Flickable) item;
            childLx += f.contentX.peek().floatValue();
            childLy += f.contentY.peek().floatValue();
        }
        List<Item> ordered = zOrdered(item.children);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MouseArea hit = hitTestMouseArea(ordered.get(i), childLx, childLy);
            if (hit != null) return hit;
        }
        return item instanceof MouseArea ? (MouseArea) item : null;
    }

    private float[] localCoords(Item target, float rootX, float rootY) {
        float ox = 0, oy = 0;
        Item cur = target;
        while (cur != null) {
            ox += cur.x.peek().floatValue();
            oy += cur.y.peek().floatValue();
            Item p = cur.parent.peek();
            if (p instanceof Flickable) {
                Flickable f = (Flickable) p;
                ox -= f.contentX.peek().floatValue();
                oy -= f.contentY.peek().floatValue();
            }
            cur = p;
        }
        return new float[]{rootX - ox, rootY - oy};
    }

    public void renderFrame(SurfaceBackend backend) {
        dirty.install();
        try {
            tickAnimations(root, System.nanoTime());
            dirty.flush();
            Canvas canvas = backend.acquireCanvas();
            renderer.render(canvas, root);
            backend.present();
        } finally {
            dirty.uninstall();
        }
    }

    public void tickAnimations(long nowNanos) {
        if (root == null) return;
        tickAnimations(root, nowNanos);
    }

    private void tickAnimations(Item node, long now) {
        if (node == null) return;
        if (node instanceof Animatable) {
            ((Animatable) node).tick(now);
        }
        for (int i = node.children.size() - 1; i >= 0; i--) {
            Item c = node.children.get(i);
            tickAnimations(c, now);
            if (c instanceof NumberAnimation) {
                NumberAnimation a = (NumberAnimation) c;
                if (a.ephemeral && !Boolean.TRUE.equals(a.running.peek())) {
                    node.children.remove(i);
                }
            }
        }
    }

    public DirtyQueue dirtyQueue() {
        return dirty;
    }

    public Renderer renderer() {
        return renderer;
    }

    public void dispose() {
        renderer.dispose();
    }
}
