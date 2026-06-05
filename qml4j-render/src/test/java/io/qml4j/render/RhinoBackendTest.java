package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.engine.js.RhinoBinding;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Phase 1 of the Rhino migration: simple property-expression bindings are evaluated
// by the embedded JS engine, not the ASM codegen. Confirms the binding really runs
// through RhinoBinding AND stays reactive through QmlView.load.
class RhinoBackendTest {

    private static void flush(QmlView v) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
    }

    private static Object bindingOf(Property<?> p) throws Exception {
        Field f = Property.class.getDeclaredField("binding");
        f.setAccessible(true);
        return f.get(p);
    }

    @Test
    void simpleExpressionBindingRunsOnRhinoAndStaysReactive() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  width: height * 2 + 10\n" +
            "  z: width > 50 ? 1 : 0\n" +
            "}");
        flush(v);

        // The arithmetic binding went to Rhino (not the ASM backend).
        assertTrue(bindingOf(root.width) instanceof RhinoBinding,
            "width binding should be a RhinoBinding, was " + bindingOf(root.width));

        root.height.set(5);
        flush(v);
        assertEquals(20, root.width.peek().intValue());   // 5*2+10
        assertEquals(0, root.z.peek().intValue());        // 20 > 50 ? 1 : 0

        root.height.set(100);
        flush(v);
        assertEquals(210, root.width.peek().intValue());  // 100*2+10
        assertEquals(1, root.z.peek().intValue());        // 210 > 50 ? 1 : 0
    }

    @Test
    void namespaceCallAndEnumBindingsRunOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  height: 40\n" +
            "  width: Math.max(height, 100)\n" +
            "}");
        flush(v);

        assertTrue(bindingOf(root.width) instanceof RhinoBinding);
        assertEquals(100, root.width.peek().intValue());  // max(40, 100)

        root.height.set(250);
        flush(v);
        assertEquals(250, root.width.peek().intValue());  // max(250, 100)
    }

    @Test
    void objectMethodCallBindingRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  function dbl(x) { return x * 2 }\n" +
            "  height: 15\n" +
            "  width: root.dbl(height)\n" +
            "}");
        flush(v);

        // root.dbl(height): Rhino resolves the id, calls the QML function via JavaMember.
        assertTrue(bindingOf(root.width) instanceof RhinoBinding);
        assertEquals(30, root.width.peek().intValue());   // dbl(15)

        root.height.set(21);
        flush(v);
        assertEquals(42, root.width.peek().intValue());   // dbl(21)
    }
}
