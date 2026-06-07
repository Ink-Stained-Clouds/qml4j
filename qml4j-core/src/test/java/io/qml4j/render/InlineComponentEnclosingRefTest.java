package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// An inline component reads the enclosing file's root-level property (ColorPage's
// SchemeColumn binds `model: colorGroups`). Resolved at runtime via the parent chain
// to the enclosing root, so its bindings must flush AFTER its parent is set.
class InlineComponentEnclosingRefTest {

    @Test
    void inlineComponentReadsEnclosingRootProperty() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: page\n"
            + "  property int base: 7\n"
            + "  component Tile: Item { property int echoed: base * 2 }\n"
            + "  Tile { id: t }\n"
            + "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        Item tile = root.children.get(0);
        assertEquals(14, asInt(tile, "echoed"), "inline reads enclosing root property `base`");
    }

    private static int asInt(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).intValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
