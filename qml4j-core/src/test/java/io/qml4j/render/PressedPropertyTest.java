package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.MouseArea;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// MouseArea exposes `pressed` as a read-only bool property (Qt name), while
// the press signal is still reachable via the onPressed handler. Both coexist.
class PressedPropertyTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static Object prop(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void pressedIsBoolPropertyAndOnPressedStillFires() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 200\n" +
            "  property bool down: ma.pressed\n" +
            "  property int presses: 0\n" +
            "  MouseArea { id: ma; width: 200; height: 200\n" +
            "    onPressed: parent.presses = parent.presses + 1\n" +
            "  }\n" +
            "}");
        MouseArea ma = (MouseArea) root.children.get(0);

        assertEquals(Boolean.FALSE, ma.pressed.peek());
        assertEquals(Boolean.FALSE, prop(root, "down"));

        v.dispatchPointerDown(10, 10);
        assertEquals(1, ((Number) prop(root, "presses")).intValue());
        assertEquals(Boolean.TRUE, ma.pressed.peek());
        assertEquals(Boolean.TRUE, prop(root, "down"));

        v.dispatchPointerUp(10, 10);
        assertEquals(Boolean.FALSE, ma.pressed.peek());
        assertEquals(Boolean.FALSE, prop(root, "down"));
    }
}
