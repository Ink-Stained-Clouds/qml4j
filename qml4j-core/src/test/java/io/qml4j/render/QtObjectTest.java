package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QtObjectTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void nestedQtObjectPropertyReadTwoDots() {
        Item root = newView().load(
            "Item {\n" +
            "  property QtObject palette: QtObject {\n" +
            "    property color primary: \"#6750a4\"\n" +
            "    property int weight: 500\n" +
            "  }\n" +
            "  property color c: palette.primary\n" +
            "  property int w: palette.weight\n" +
            "}");
        assertEquals("#6750a4", readProp(root, "c"));
        assertEquals(500L, readProp(root, "w"));
    }

    @Test
    void deeplyNestedThreeDots() {
        Item root = newView().load(
            "Item {\n" +
            "  property QtObject theme: QtObject {\n" +
            "    property QtObject color: QtObject {\n" +
            "      property color primary: \"#112233\"\n" +
            "    }\n" +
            "  }\n" +
            "  property color c: theme.color.primary\n" +
            "}");
        assertEquals("#112233", readProp(root, "c"));
    }

    @Test
    void qtObjectAsPlainChildHolder() {
        Item root = newView().load(
            "Item {\n" +
            "  QtObject { id: store; property int count: 7 }\n" +
            "  property int n: store.count\n" +
            "}");
        assertEquals(7L, readProp(root, "n"));
    }

    @Test
    void singletonQtObjectThemeNestedGroups() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("theme/Theme.qml",
            ("pragma Singleton\n" +
             "import QtQuick\n" +
             "QtObject {\n" +
             "  property QtObject color: QtObject {\n" +
             "    property color primary: \"#ff8800\"\n" +
             "    property color surface: \"#1a1f26\"\n" +
             "  }\n" +
             "  property int padding: 16\n" +
             "}").getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"theme\"\n" +
            "Rectangle {\n" +
            "  color: Theme.color.primary\n" +
            "  property color surf: Theme.color.surface\n" +
            "  property int p: Theme.padding\n" +
            "}");
        assertEquals("#ff8800", ((io.qml4j.render.items.core.Rectangle) root).color.peek());
        assertEquals("#1a1f26", readProp(root, "surf"));
        assertEquals(16L, readProp(root, "p"));
    }

    @Test
    void nestedQtObjectBindingReactsToChange() {
        Item root = newView().load(
            "Item {\n" +
            "  property QtObject m: QtObject { property int v: 1 }\n" +
            "  property int doubled: m.v * 2\n" +
            "}");
        assertEquals(2L, readProp(root, "doubled"));
        Object m = readProp(root, "m");
        try {
            Field vf = m.getClass().getField("v");
            @SuppressWarnings("unchecked")
            Property<Object> vp = (Property<Object>) vf.get(m);
            vp.set(10);
        } catch (Exception e) { throw new RuntimeException(e); }
        assertEquals(20L, readProp(root, "doubled"));
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
