package io.qml4j.render;

import io.qml4j.render.items.Animatable;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.NumberAnimation;

import io.github.humbleui.skija.Canvas;
import io.qml4j.compiler.CompiledUnit;
import io.qml4j.compiler.bytecode.QmlCompiler;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.engine.classloader.ClassLoaderBackend;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.QmlEngine;
import io.qml4j.parser.Qml4j;
import io.qml4j.parser.ast.Ast;

import java.util.Map;

public final class QmlView {

    private final QmlEngine engine;
    private final TypeRegistry types;
    private final QmlCompiler compiler = new QmlCompiler();
    private final Renderer renderer = new Renderer();
    private final DirtyQueue dirty = new DirtyQueue();
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
        renderer.setResourceLoader(loader);
        return this;
    }

    public Item load(String qml) {
        root = instantiate(qml);
        return root;
    }

    private Item instantiate(String qml) {
        Ast.QmlDocument doc = Qml4j.parse(qml);
        CompiledUnit unit = compiler.compile(doc, types);
        ClassLoaderBackend backend = engine.backend();
        Map<String, Class<?>> defined = backend.defineClasses(unit.classes());
        Class<?> rootClass = defined.get(unit.rootClassName());
        if (rootClass == null) {
            throw new IllegalStateException("compiled unit missing root class");
        }
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

    public Item root() {
        return root;
    }

    public boolean dispatchClick(float x, float y) {
        if (root == null) return false;
        return hitTest(root, x, y);
    }

    private boolean hitTest(Item item, float x, float y) {
        if (!item.visible.peek()) return false;
        float ix = item.x.peek().floatValue();
        float iy = item.y.peek().floatValue();
        float w = item.width.peek().floatValue();
        float h = item.height.peek().floatValue();
        float lx = x - ix;
        float ly = y - iy;
        if (lx < 0 || ly < 0 || lx > w || ly > h) return false;
        for (int i = item.children.size() - 1; i >= 0; i--) {
            if (hitTest(item.children.get(i), lx, ly)) return true;
        }
        if (item instanceof MouseArea) {
            ((MouseArea) item).clicked.emit();
            return true;
        }
        return false;
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
