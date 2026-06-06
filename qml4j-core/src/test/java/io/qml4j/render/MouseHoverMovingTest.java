package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Flickable;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.MouseArea;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// MouseArea hover (hoverEnabled/containsMouse/entered/exited) + Flickable.moving,
// the engine-level ScrollBar prerequisites. Mirrors Qt semantics.
class MouseHoverMovingTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static long propLong(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).longValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void hoverEnabledTracksContainsMouseAndFiresEnteredExited() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 200\n" +
            "  property int ins: 0\n" +
            "  property int outs: 0\n" +
            "  MouseArea { id: ma; width: 100; height: 100\n" +
            "    hoverEnabled: true\n" +
            "    onEntered: parent.ins = parent.ins + 1\n" +
            "    onExited: parent.outs = parent.outs + 1\n" +
            "  }\n" +
            "}");
        MouseArea ma = (MouseArea) root.children.get(0);
        assertEquals(Boolean.FALSE, ma.containsMouse.peek());

        v.dispatchPointerMove(50, 50);
        assertEquals(Boolean.TRUE, ma.containsMouse.peek());
        assertEquals(1, propLong(root, "ins"));
        assertEquals(0, propLong(root, "outs"));

        v.dispatchPointerMove(150, 150); // outside the 100x100 area
        assertEquals(Boolean.FALSE, ma.containsMouse.peek());
        assertEquals(1, propLong(root, "ins"));
        assertEquals(1, propLong(root, "outs"));
    }

    @Test
    void pressSetsContainsMouseEvenWithoutHoverEnabled() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 200\n" +
            "  MouseArea { id: ma; width: 200; height: 200 }\n" +
            "}");
        MouseArea ma = (MouseArea) root.children.get(0);
        v.dispatchPointerDown(10, 10);
        assertEquals(Boolean.TRUE, ma.containsMouse.peek());
        v.dispatchPointerUp(10, 10);
        assertEquals(Boolean.FALSE, ma.containsMouse.peek());
    }

    @Test
    void flickableMovingTrueWhileDragging() {
        QmlView v = newView();
        Item root = v.load(
            "Flickable { width: 100; height: 100\n" +
            "  contentWidth: 500; contentHeight: 500\n" +
            "}");
        Flickable f = (Flickable) root;
        assertEquals(Boolean.FALSE, f.moving.peek());
        v.dispatchPointerDown(50, 50);
        assertEquals(Boolean.TRUE, f.moving.peek());
        v.dispatchPointerUp(20, 20);
        assertEquals(Boolean.FALSE, f.moving.peek());
    }
}
