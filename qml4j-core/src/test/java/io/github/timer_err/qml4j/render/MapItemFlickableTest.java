package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Flickable;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// mapFromItem/mapToItem return on-screen coordinates, accounting for an ancestor
// Flickable's scroll -- so a Menu/ComboBox popup anchored to the scene root lands at the
// trigger's visible position, not its (much larger) content position.
class MapItemFlickableTest {

    @Test
    void mapFromItemSubtractsAncestorFlickableScroll() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Item { id: root; width: 400; height: 400\n" +
            "  Flickable { id: fl; width: 400; height: 200; contentHeight: 1000\n" +
            "    Item { id: target; y: 300; width: 10; height: 10 }\n" +
            "  }\n" +
            "}");
        Flickable fl = (Flickable) reflectField(root, "fl");
        fl.contentY.set(120);
        Item target = (Item) reflectField(root, "target");

        Map<String, Object> p = root.mapFromItem(target, 0, 0);
        // content y 300, scrolled up by 120 -> visible y 180 in root coords.
        assertEquals(180.0, ((Number) p.get("y")).doubleValue(), 1e-6);
    }

    private static Object reflectField(Object root, String name) {
        try {
            return root.getClass().getField(name).get(root);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
