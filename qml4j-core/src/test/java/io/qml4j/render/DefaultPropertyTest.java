package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// `default property alias content: inner.children` — child objects placed in a
// component instance are redirected into the aliased inner container's list.
class DefaultPropertyTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void defaultListAliasRedirectsChildren() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("comp/Panel.qml",
            ("import QtQuick\n" +
             "Item {\n" +
             "  default property alias content: inner.children\n" +
             "  Column { id: inner; spacing: 2 }\n" +
             "}").getBytes());
        v.resources(files::get);

        Item root = v.load(
            "import QtQuick\n" +
            "import \"comp\"\n" +
            "Item {\n" +
            "  Panel {\n" +
            "    Rectangle { width: 10; height: 10 }\n" +
            "    Rectangle { width: 20; height: 20 }\n" +
            "  }\n" +
            "}");
        Item panel = root.children.get(0);
        // Panel's own children: just the inner Column.
        assertEquals(1, panel.children.size());
        Item inner = panel.children.get(0);
        // The two Rectangles were redirected into inner (the Column).
        assertEquals(2, inner.children.size());
        assertEquals(10.0, inner.children.get(0).width.peek().doubleValue(), 1e-6);
        assertEquals(20.0, inner.children.get(1).width.peek().doubleValue(), 1e-6);
    }

    @Test
    void objectAliasExposesInnerObject() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("comp/Badge.qml",
            ("import QtQuick\n" +
             "Item {\n" +
             "  property alias dot: theDot\n" +
             "  Rectangle { id: theDot; width: 7; height: 7; color: \"#ff0000\" }\n" +
             "}").getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import \"comp\"\n" +
            "Item {\n" +
            "  property string dotColor: badge.dot.color\n" +
            "  Badge { id: badge }\n" +
            "}");
        try {
            java.lang.reflect.Field f = root.getClass().getField("dotColor");
            Object val = ((io.qml4j.engine.binding.Property<?>) f.get(root)).peek();
            assertEquals("#ff0000", val);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
