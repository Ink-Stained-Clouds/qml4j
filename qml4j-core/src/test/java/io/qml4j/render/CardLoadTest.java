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

class CardLoadTest {
    private static byte[] res(String path) {
        try (InputStream in = CardLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsCard() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Ripple.qml", "Card.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 320; height: 220\n" +
            "  Card { type: \"elevated\"\n" +
            "    Text { text: \"Hi\" }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty());
        Item card = root.children.get(0);
        try {
            java.lang.reflect.Field f = card.getClass().getField("containerColor");
            Object cc = ((io.qml4j.engine.binding.Property<?>) f.get(card)).peek();
            org.junit.jupiter.api.Assertions.assertEquals("#f7f2fa", cc, "elevated containerColor");
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
