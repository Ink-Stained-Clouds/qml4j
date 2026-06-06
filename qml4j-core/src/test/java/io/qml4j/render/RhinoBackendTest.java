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

    // Phase 4c: a function-style (IIFE) binding `prop: { ...; return x }` runs on Rhino
    // as an immediately-invoked function and stays reactive.
    @Test
    void iifeBindingRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  height: 1\n" +
            "  width: { var base = height * 10; return base + 5 }\n" +
            "}");
        flush(v);

        assertTrue(bindingOf(root.width) instanceof RhinoBinding,
            "function-style binding should be a RhinoBinding");
        assertEquals(15, root.width.peek().intValue());   // 1*10 + 5

        root.height.set(4);
        flush(v);
        assertEquals(45, root.width.peek().intValue());   // 4*10 + 5
    }

    // Phase 6 (the flip): bindings the old structural predicate rejected -- a template
    // literal, an array/object literal, a bare call to a root function -- now route to
    // Rhino too, since their free identifiers all resolve in the QmlScope.
    @Test
    void widenedBindingsRunOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  function dbl(x) { return x * 2 }\n" +
            "  height: 7\n" +
            "  property string label: `h=${height}`\n" +    // template literal
            "  property var nums: [height, height + 1]\n" +  // array literal
            "  width: dbl(height)\n" +                       // bare call to root function
            "}");
        flush(v);

        Property<?> label = (Property<?>) root.getClass().getField("label").get(root);
        Property<?> nums = (Property<?>) root.getClass().getField("nums").get(root);
        assertTrue(bindingOf(label) instanceof RhinoBinding, "template-literal binding");
        assertTrue(bindingOf(root.width) instanceof RhinoBinding, "bare-call binding");
        assertEquals("h=7", label.peek());
        assertEquals(14, root.width.peek().intValue());
        @SuppressWarnings("unchecked")
        java.util.List<Object> ns = (java.util.List<Object>) nums.peek();
        assertEquals(2, ns.size());

        root.height.set(20);
        flush(v);
        assertEquals("h=20", label.peek());
        assertEquals(40, root.width.peek().intValue());
    }

    // Phase 4 tail: a grouped binding (border.width: ...) runs on Rhino, bound to the
    // value-type group's Property, and stays reactive.
    @Test
    void groupedBindingRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  width: 10\n" +
            "  border.width: width > 5 ? 3 : 1\n" +
            "}");
        flush(v);

        Object border = root.getClass().getField("border").get(root);
        Property<?> borderWidth = (Property<?>) border.getClass().getField("width").get(border);
        assertTrue(bindingOf(borderWidth) instanceof RhinoBinding,
            "grouped binding should be a RhinoBinding");
        assertEquals(3, ((Number) borderWidth.peek()).intValue());   // width 10 > 5

        root.width.set(2);
        flush(v);
        assertEquals(1, ((Number) borderWidth.peek()).intValue());   // width 2 <= 5
    }
}
