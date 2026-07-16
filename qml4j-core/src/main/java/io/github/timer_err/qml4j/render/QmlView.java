package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.animation.Animatable;
import io.github.timer_err.qml4j.render.items.animation.GroupAnimation;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.animation.PropertyAnimation;
import io.github.timer_err.qml4j.render.items.input.TextEditable;
import io.github.timer_err.qml4j.render.items.input.TextInput;

import io.github.humbleui.skija.Canvas;
import io.github.timer_err.qml4j.compiler.TypeRegistry;
import io.github.timer_err.qml4j.compiler.bytecode.rhino.RhinoScope;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.js.JsRuntime;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;

public final class QmlView {

    private final Renderer renderer = new Renderer();
    private final DirtyQueue dirty = new DirtyQueue();
    private final Loader loader;
    private final FocusManager focus = new FocusManager();
    private final EventDispatcher events = new EventDispatcher(focus, renderer);
    private Item root;

    public QmlView(QmlEngine engine, TypeRegistry types) {
        this.loader = new Loader(engine, types);
        renderer.setComponentFactory(loader);
    }

    public static QmlView withStockTypes(QmlEngine engine) {
        return new QmlView(engine, StockTypes.registry());
    }

    public QmlView resources(ResourceLoader res) {
        loader.setResources(res);
        renderer.setResourceLoader(res);
        return this;
    }

    /** Override the UI font (regular + medium) so the whole scene renders with an
     *  app-bundled face (Latin + CJK). Call before the first frame. */
    public QmlView uiTypefaces(byte[] regular, byte[] medium) {
        renderer.setUiTypefaces(regular, medium);
        return this;
    }

    /** Provide a dedicated CJK face (optional; the default font covers CJK otherwise). */
    public QmlView cjkTypeface(byte[] bytes) {
        renderer.setCjkTypeface(bytes);
        return this;
    }

    /** Provide the icon face (e.g. Material Symbols) the scene's icon glyphs need. */
    public QmlView iconTypeface(byte[] bytes) {
        renderer.setIconTypeface(bytes);
        return this;
    }

    // Expose a host value to QML under `name` (QML's setContextProperty): bindings resolve
    // it as a free identifier. Register before load() so the compiler accepts it.
    public QmlView context(String name, Object value) {
        RhinoScope.registerContextProperty(name);
        JsRuntime.putGlobal(name, value);
        return this;
    }

    public Item load(String qml) {
        return load(qml, "");
    }

    // baseDir is the document's directory (relative to the resource root), so its
    // relative file imports resolve correctly -- e.g. loading "pages/X.qml" passes "pages".
    public Item load(String qml, String baseDir) {
        root = loader.instantiate(qml, baseDir);
        focus.setRoot(root);
        events.setRoot(root);
        root.installFocusHook(focus::setFocus);
        root.initStateBindingsTree();
        focus.scanInitialFocus(root);
        return root;
    }

    public Item root() {
        return root;
    }

    /** First item in the tree whose objectName equals {@code name}, or null. Lets a host
     *  locate a tagged subtree (e.g. to render it in a separate pass). */
    @SuppressWarnings("unused") // called by the host shell, not from within qml4j
    public Item findByObjectName(String name) {
        return findByObjectName(root, name);
    }

    private static Item findByObjectName(Item node, String name) {
        if (node == null) return null;
        if (name.equals(node.objectName.peek())) return node;
        for (int i = 0; i < node.children.size(); i++) {
            Item r = findByObjectName(node.children.get(i), name);
            if (r != null) return r;
        }
        return null;
    }

    public void setClipboard(Clipboard cb) {
        events.setClipboard(cb);
    }

    public boolean copy() {
        return events.copy();
    }

    public boolean cut() {
        return events.cut();
    }

    public boolean paste() {
        return events.paste();
    }

    public interface FocusListener {
        void onFocusChanged(Item newFocus, Item oldFocus);
    }

    public void setFocusListener(FocusListener l) {
        focus.setFocusListener(l);
    }

    /** Notified as each compound component is compiled during {@link #load} —
     *  drives a host splash/progress while the QML tree compiles. */
    public interface CompileProgressListener {
        @SuppressWarnings("unused")
        void onComponentCompiled(String name, int compiledCount);
    }

    @SuppressWarnings("unused")
    public void setCompileProgressListener(CompileProgressListener l) {
        loader.setProgressListener(l);
    }

    public Item focused() {
        return focus.focused();
    }

    public void setFocus(Item it) {
        focus.setFocus(it);
    }

    @SuppressWarnings("unused")
    public void clearFocus() {
        focus.clearFocus();
    }

    public boolean dispatchKey(int keyCode, String text, boolean down) {
        return events.dispatchKey(keyCode, text, down, false);
    }

    public boolean dispatchKey(int keyCode, String text, boolean down, boolean shift) {
        return events.dispatchKey(keyCode, text, down, shift);
    }

    public static final int KEY_BACKSPACE = -1;
    public static final int KEY_ENTER = -2;
    public static final int KEY_LEFT = -3;
    public static final int KEY_RIGHT = -4;
    public static final int KEY_HOME = -5;
    public static final int KEY_END = -6;
    public static final int KEY_UP = -7;
    public static final int KEY_DOWN = -8;
    public static final int KEY_ESCAPE = -9;
    public static final int KEY_TAB = -10;
    public static final int KEY_BACKTAB = -11;

    public boolean dispatchClick(float x, float y) {
        return events.dispatchClick(x, y);
    }

    public boolean dispatchPointerDown(float x, float y) {
        return events.dispatchPointerDown(x, y);
    }

    /** button is a Qt.MouseButton value: LeftButton=1, RightButton=2, MiddleButton=4. */
    public boolean dispatchPointerDown(float x, float y, int button) {
        return events.dispatchPointerDown(x, y, button);
    }

    public boolean dispatchPointerMove(float x, float y) {
        return events.dispatchPointerMove(x, y);
    }

    public boolean dispatchPointerUp(float x, float y) {
        return events.dispatchPointerUp(x, y);
    }

    /** button is a Qt.MouseButton value: LeftButton=1, RightButton=2, MiddleButton=4. */
    public boolean dispatchPointerUp(float x, float y, int button) {
        return events.dispatchPointerUp(x, y, button);
    }

    public boolean dispatchWheel(float x, float y, float dx, float dy) {
        return events.dispatchWheel(x, y, dx, dy);
    }

    @SuppressWarnings("unused")
    public TextEditable pickTextEditable(float x, float y) {
        return events.pickTextEditable(x, y);
    }

    @SuppressWarnings("unused")
    public TextInput pickTextInput(float x, float y) {
        return events.pickTextInput(x, y);
    }

    private long fpsLastNanos;
    private double fpsSmoothed;
    private long renderedVersion = -1;

    public void renderFrame(SurfaceBackend backend) {
        dirty.install();
        try {
            long now = System.nanoTime();
            tickAnimations(root, now);
            dirty.flush();
            // Idle-frame fast path: if nothing in the scene changed since the last full
            // layout (no animation/timer/input/binding touched a property), skip the layout
            // pass and repaint with cached geometry -- so an idle UI over a game loop costs
            // only its paint, not a full re-layout every frame.
            boolean skipLayout = Property.changeVersion() == renderedVersion;
            Canvas canvas = backend.acquireCanvas();
            renderer.setGpuContext(backend.recordingContext());
            renderer.render(canvas, root, skipLayout);
            renderedVersion = Property.changeVersion();
            if (renderer.fpsOverlayEnabled()) drawFpsOverlay(canvas, now);
            backend.present();
        } finally {
            dirty.uninstall();
        }
    }

    // Smooth the inter-frame interval (EMA) into an FPS reading and draw it top-right.
    private void drawFpsOverlay(Canvas canvas, long now) {
        if (fpsLastNanos > 0) {
            double inst = 1e9 / Math.max(1, now - fpsLastNanos);
            fpsSmoothed = fpsSmoothed == 0 ? inst : fpsSmoothed * 0.9 + inst * 0.1;
        }
        fpsLastNanos = now;
        if (root != null) renderer.drawFpsOverlay(canvas, root.width.peekFloat(), fpsSmoothed);
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
        if (node instanceof GroupAnimation) return;
        for (int i = node.children.size() - 1; i >= 0; i--) {
            Item c = node.children.get(i);
            tickAnimations(c, now);
            if (c instanceof PropertyAnimation) {
                PropertyAnimation a = (PropertyAnimation) c;
                if (a.ephemeral && !Boolean.TRUE.equals(a.running.peek())) {
                    node.children.remove(i);
                }
            }
        }
        // Non-visual children (Behavior) animate too, but live off the children list.
        for (Item r : node.resources) tickAnimations(r, now);
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
