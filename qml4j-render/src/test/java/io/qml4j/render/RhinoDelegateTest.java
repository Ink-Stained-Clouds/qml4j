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
}
