package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QmldirTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void qmldirMapsTypeNameToCustomFilename() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("widgets/qmldir",
            "# header\nCard 1.0 cards/Card.qml\n".getBytes());
        files.put("widgets/cards/Card.qml",
            "Rectangle { width: 33; height: 22 }".getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"widgets\"\n" +
            "Item { Card { } }");
        Rectangle card = (Rectangle) root.children.get(0);
        assertEquals(33L, card.width.peek().longValue());
    }

    @Test
    void qmldirSingletonMarkerIsParsedButLoadsClassForNow() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("theme/qmldir",
            "singleton Theme 1.0 Theme.qml\n".getBytes());
        files.put("theme/Theme.qml",
            "Rectangle { width: 1; height: 1 }".getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"theme\"\n" +
            "Item { Theme { } }");
        Rectangle t = (Rectangle) root.children.get(0);
        assertEquals(1L, t.width.peek().longValue());
    }

    @Test
    void qmldirAbsentFallsThroughToCapitalisedFilename() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("widgets/Card.qml",
            "Rectangle { width: 7; height: 7 }".getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"widgets\"\n" +
            "Item { Card { } }");
        Rectangle card = (Rectangle) root.children.get(0);
        assertEquals(7L, card.width.peek().longValue());
    }
}
