package io.qml4j.render;

import io.qml4j.render.items.Animatable;
import io.qml4j.render.items.Drag;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.NumberAnimation;

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
        return root;
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

    public boolean dispatchClick(float x, float y) {
        return dispatchPointerDown(x, y) && dispatchPointerUp(x, y);
    }

    public boolean dispatchPointerDown(float x, float y) {
        if (root == null) return false;
        MouseArea hit = hitTestMouseArea(root, x, y);
        if (hit == null) return false;
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

    public boolean dispatchPointerMove(float x, float y) {
        if (captured == null) return false;
        float[] local = localCoords(captured, x, y);
        captured.mouseX.set(local[0]);
        captured.mouseY.set(local[1]);
        applyDrag(x, y);
        captured.positionChanged.emit();
        return true;
    }

    public boolean dispatchPointerUp(float x, float y) {
        if (captured == null) return false;
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

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
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
        List<Item> ordered = zOrdered(item.children);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MouseArea hit = hitTestMouseArea(ordered.get(i), lx, ly);
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
            cur = cur.parent.peek();
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
