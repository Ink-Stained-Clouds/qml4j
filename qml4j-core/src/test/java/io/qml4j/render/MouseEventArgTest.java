package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// MouseArea signal handlers receive a MouseEvent arg (Qt: onPressed(mouse)),
// with local x/y. Both the arrow form and reading mouse.x/.y must work.
class MouseEventArgTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static double propD(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).doubleValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void onPressedArrowReceivesLocalMouseCoords() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "Item { width: 200; height: 200\n" +
            "  property real px: -1\n" +
            "  property real py: -1\n" +
            "  MouseArea { x: 20; y: 30; width: 100; height: 100\n" +
            "    onPressed: (mouse) => { parent.px = mouse.x; parent.py = mouse.y }\n" +
            "  }\n" +
            "}");
        v.dispatchPointerDown(50, 70); // local to the area at (20,30) -> (30,40)
        assertEquals(30.0, propD(root, "px"), 1e-6);
        assertEquals(40.0, propD(root, "py"), 1e-6);
    }

    @Test
    void onPositionChangedArrowReceivesMouseCoords() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "Item { width: 200; height: 200\n" +
            "  property real mx: -1\n" +
            "  MouseArea { x: 0; y: 0; width: 200; height: 200\n" +
            "    onPositionChanged: (mouse) => { parent.mx = mouse.x }\n" +
            "  }\n" +
            "}");
        v.dispatchPointerDown(10, 10);
        v.dispatchPointerMove(75, 10);
        assertEquals(75.0, propD(root, "mx"), 1e-6);
    }
}
