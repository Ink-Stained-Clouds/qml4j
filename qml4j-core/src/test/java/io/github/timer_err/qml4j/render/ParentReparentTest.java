package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A `parent: other` binding moves the item into the other item's children (Qt), so it
// renders there rather than at its declaration site -- the MD3 Snackbar/ToolTip pattern
// of reparenting into a high-z overlay.
class ParentReparentTest {

    @Test
    void parentBindingMovesItemIntoTargetChildren() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Item { id: root\n" +
            "  Item { id: overlay; z: 99 }\n" +
            "  Rectangle { id: floater; parent: overlay }\n" +
            "}");
        Item overlay = (Item) reflectField(root, "overlay");
        Item floater = (Item) reflectField(root, "floater");

        assertSame(overlay, floater.parent.peek());
        assertTrue(overlay.children.contains(floater), "floater moved into overlay's children");
        assertFalse(root.children.contains(floater), "floater no longer at its declaration site");
    }

    private static Object reflectField(Object root, String name) {
        try {
            return root.getClass().getField(name).get(root);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
