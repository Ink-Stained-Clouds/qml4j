package io.qml4j.render;

import io.qml4j.render.items.animation.Animatable;
import io.qml4j.render.items.animation.GroupAnimation;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.animation.PropertyAnimation;
import io.qml4j.render.items.input.TextEditable;
import io.qml4j.render.items.input.TextInput;

import io.github.humbleui.skija.Canvas;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.compiler.bytecode.rhino.RhinoScope;
import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.js.JsRuntime;
import io.qml4j.engine.binding.DirtyQueue;

public final class QmlView {

    private final Renderer renderer = new Renderer();
    private final DirtyQueue dirty = new DirtyQueue();
    private final Loader loader;
    private final FocusManager focus = new FocusManager();
    private final EventDispatcher events = new EventDispatcher(focus, renderer);
    private Item root;

    public QmlView(QmlEngine engine, TypeRegistry types) {
        this.loader = new Loader(engine, types);
        renderer.setComponentFactory(loader::instantiate);
    }

    public static QmlView withStockTypes(QmlEngine engine) {
        return new QmlView(engine, StockTypes.registry());
    }

    public QmlView resources(ResourceLoader res) {
        loader.setResources(res);
        renderer.setResourceLoader(res);
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
        root = loader.instantiate(qml);
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

    public Item focused() {
        return focus.focused();
    }

    public void setFocus(Item it) {
        focus.setFocus(it);
    }

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

    public boolean dispatchPointerMove(float x, float y) {
        return events.dispatchPointerMove(x, y);
    }

    public boolean dispatchPointerUp(float x, float y) {
        return events.dispatchPointerUp(x, y);
    }

    public TextEditable pickTextEditable(float x, float y) {
        return events.pickTextEditable(x, y);
    }

    public TextInput pickTextInput(float x, float y) {
        return events.pickTextInput(x, y);
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
