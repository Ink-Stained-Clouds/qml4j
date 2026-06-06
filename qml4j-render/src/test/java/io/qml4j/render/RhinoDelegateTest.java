package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.engine.js.RhinoBinding;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Phase 5: a simple-expression binding inside a Repeater delegate runs on Rhino. The
// delegate scope resolves index / modelData / enclosing-scene names through
// RuntimeHelpers.delegateLookup, the same walk the ASM backend uses.
class RhinoDelegateTest {

    private static Object bindingOf(Property<?> p) throws Exception {
        Field f = Property.class.getDeclaredField("binding");
        f.setAccessible(true);
        return f.get(p);
    }

    @Test
    void delegateBindingRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property var items: [{ v: 10 }, { v: 20 }, { v: 30 }]\n" +
            "  property int bump: 1\n" +
            "  Repeater {\n" +
            "    model: root.items\n" +
            "    Text { width: modelData.v * 2 + index + root.bump }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        List<Text> rows = new ArrayList<>();
        for (Item c : root.children) if (c instanceof Text) rows.add((Text) c);
        assertEquals(3, rows.size());

        assertTrue(bindingOf(rows.get(0).width) instanceof RhinoBinding,
            "delegate binding should be a RhinoBinding");
        assertEquals(21, rows.get(0).width.peek().intValue());   // 10*2 + 0 + 1
        assertEquals(42, rows.get(1).width.peek().intValue());   // 20*2 + 1 + 1
        assertEquals(63, rows.get(2).width.peek().intValue());   // 30*2 + 2 + 1
    }

    // Phase 5b: a function-style (IIFE) binding and a grouped binding inside a delegate
    // also run on Rhino, resolving modelData/index through delegateLookup.
    @Test
    void delegateIifeAndGroupedBindingsRunOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property var items: [{ v: 10 }, { v: 20 }]\n" +
            "  Repeater {\n" +
            "    model: root.items\n" +
            "    Rectangle {\n" +
            "      width: { var base = modelData.v; if (index === 0) return base; return base + 100 }\n" +
            "      border.width: index === 0 ? 3 : 1\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        List<io.qml4j.render.items.Rectangle> rects = new ArrayList<>();
        for (Item c : root.children) {
            if (c instanceof io.qml4j.render.items.Rectangle) rects.add((io.qml4j.render.items.Rectangle) c);
        }
        assertEquals(2, rects.size());

        assertTrue(bindingOf(rects.get(0).width) instanceof RhinoBinding, "delegate IIFE binding");
        assertEquals(10, rects.get(0).width.peek().intValue());    // base, index 0
        assertEquals(120, rects.get(1).width.peek().intValue());   // base + 100, index 1

        assertTrue(bindingOf(rects.get(0).border.width) instanceof RhinoBinding, "delegate grouped binding");
        assertEquals(3, rects.get(0).border.width.peek().intValue());
        assertEquals(1, rects.get(1).border.width.peek().intValue());
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<io.qml4j.engine.SignalHandler> handlersOf(io.qml4j.engine.Signal s) throws Exception {
        Field f = io.qml4j.engine.Signal.class.getDeclaredField("handlers");
        f.setAccessible(true);
        return (java.util.List<io.qml4j.engine.SignalHandler>) f.get(s);
    }

    // Phase 5c: a signal handler inside a delegate runs on Rhino, resolving modelData /
    // index / enclosing-id member access through delegateLookup.
    @Test
    void delegateHandlerRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property int total: 0\n" +
            "  property var items: [{ v: 5 }, { v: 7 }]\n" +
            "  Repeater {\n" +
            "    model: root.items\n" +
            "    Rectangle {\n" +
            "      signal tapped()\n" +
            "      onTapped: root.total = root.total + modelData.v + index\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        List<io.qml4j.render.items.Rectangle> rects = new ArrayList<>();
        for (Item c : root.children) {
            if (c instanceof io.qml4j.render.items.Rectangle) rects.add((io.qml4j.render.items.Rectangle) c);
        }
        assertEquals(2, rects.size());

        for (io.qml4j.render.items.Rectangle r : rects) {
            io.qml4j.engine.Signal tapped = (io.qml4j.engine.Signal) r.getClass().getField("tapped").get(r);
            assertTrue(handlersOf(tapped).get(0) instanceof io.qml4j.engine.js.RhinoHandler,
                "delegate handler should be a RhinoHandler");
            tapped.emit();
        }
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        io.qml4j.engine.binding.Property<?> total =
            (io.qml4j.engine.binding.Property<?>) root.getClass().getField("total").get(root);
        assertEquals(13, ((Number) total.peek()).intValue());   // (5+0) + (7+1)
    }
}
