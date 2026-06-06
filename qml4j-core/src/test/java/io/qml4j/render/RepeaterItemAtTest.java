package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Repeater.itemAt(index)/count() expose delegate instances to QML -- MD3 Tabs reads
// the current tab's geometry through itemAt() to place its sliding indicator.
class RepeaterItemAtTest {

    private static double readD(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).doubleValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Object call(Item root, String fn, Object arg) {
        try {
            for (java.lang.reflect.Method m : root.getClass().getMethods()) {
                if (m.getName().equals(fn) && m.getParameterCount() == 1) return m.invoke(root, arg);
            }
            throw new NoSuchMethodException(fn);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void itemAtReturnsDelegateGeometry() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item { id: scene; width: 300; height: 60\n"
            + "  property real probedX: -1\n"
            + "  property int n: -1\n"
            + "  Row { id: row; anchors.fill: parent\n"
            + "    Repeater { id: rep; model: 3\n"
            + "      delegate: Rectangle { width: 100; height: 60 } }\n"
            + "  }\n"
            + "  function probe(i) { scene.n = rep.count(); var it = rep.itemAt(i); scene.probedX = it ? it.x : -999 }\n"
            + "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); new Renderer().layoutOnly(root); } finally { dq.uninstall(); }

        call(root, "probe", 2L);
        assertEquals(3.0, readD(root, "n"), 1e-9, "count() == 3");
        assertEquals(200.0, readD(root, "probedX"), 1e-6, "itemAt(2).x == third tab x in the Row");
    }
}
