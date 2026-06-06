package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChipLoadTest {
    private static byte[] res(String path) {
        try (InputStream in = ChipLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsChip() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Ripple.qml", "Chip.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 320; height: 120\n" +
            "  Chip { type: \"filter\"; text: \"Filter\"; selected: true }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty());
        Item chip = root.children.get(0);
        try {
            java.lang.reflect.Field f = chip.getClass().getField("containerColor");
            Object cc = ((io.qml4j.engine.binding.Property<?>) f.get(chip)).peek();
            org.junit.jupiter.api.Assertions.assertEquals("#e8def8", cc,
                "filter+selected -> secondaryContainer");
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
