package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportAliasTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void moduleAliasResolvesToStockType() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick 2.15 as Q\n" +
            "Q.Rectangle {\n" +
            "  width: 50; height: 20\n" +
            "  Q.Rectangle { width: 10; height: 10 }\n" +
            "}");
        assertTrue(root instanceof Rectangle);
        assertEquals(1, root.children.size());
        assertTrue(root.children.get(0) instanceof Rectangle);
    }

    @Test
    void barePrefixWithoutAliasIsRejected() {
        QmlView v = newView();
        assertThrows(IllegalArgumentException.class, () ->
            v.load(
                "import QtQuick 2.15\n" +
                "Z.Rectangle { }"));
    }

    @Test
    void aliasOnStringImportAlsoCarriesPrefix() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("widgets/Card.qml",
            "Rectangle { width: 40; height: 20 }".getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"widgets\" as W\n" +
            "Item {\n" +
            "  W.Card { }\n" +
            "}");
        assertNotNull(root);
        assertEquals(1, root.children.size());
        Rectangle card = (Rectangle) root.children.get(0);
        assertEquals(40L, card.width.peek().longValue());
    }
}
