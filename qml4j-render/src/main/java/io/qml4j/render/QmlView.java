package io.qml4j.render;

import io.github.humbleui.skija.Canvas;
import io.qml4j.compiler.CompiledUnit;
import io.qml4j.compiler.QmlCompiler;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.engine.ClassLoaderBackend;
import io.qml4j.engine.QmlEngine;
import io.qml4j.parser.Qml4j;
import io.qml4j.parser.ast.Ast;

import java.util.Map;

public final class QmlView {

    private final QmlEngine engine;
    private final TypeRegistry types;
    private final QmlCompiler compiler = new QmlCompiler();
    private final Renderer renderer = new Renderer();
    private Item root;

    public QmlView(QmlEngine engine, TypeRegistry types) {
        this.engine = engine;
        this.types = types;
    }

    public static QmlView withStockTypes(QmlEngine engine) {
        return new QmlView(engine, StockTypes.registry());
    }

    public Item load(String qml) {
        Ast.QmlDocument doc = Qml4j.parse(qml);
        CompiledUnit unit = compiler.compile(doc, types);
        ClassLoaderBackend backend = engine.backend();
        Class<?> rootClass = null;
        for (Map.Entry<String, byte[]> e : unit.classes().entrySet()) {
            Class<?> c = backend.defineClass(e.getKey(), e.getValue());
            if (e.getKey().equals(unit.rootClassName())) rootClass = c;
        }
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
        root = (Item) inst;
        return root;
    }

    public Item root() {
        return root;
    }

    public void renderFrame(SurfaceBackend backend) {
        Canvas canvas = backend.acquireCanvas();
        renderer.render(canvas, root);
        backend.present();
    }

    public Renderer renderer() {
        return renderer;
    }

    public void dispose() {
        renderer.dispose();
    }
}
