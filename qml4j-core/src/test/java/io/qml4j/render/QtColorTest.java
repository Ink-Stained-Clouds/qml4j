package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Qt.color(str) -> channels 0..1, round-tripped through Qt.rgba; plus the
// readonly property modifier (accepted as a plain property).
class QtColorTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static Object prop(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void qtColorChannelsAndReadonly() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "Item {\n" +
            "  readonly property string base: \"#80a0c0\"\n" +
            "  property real red: Qt.color(base).r\n" +
            "  property real green: Qt.color(base).g\n" +
            "  property string back: Qt.rgba(Qt.color(base).r, Qt.color(base).g, Qt.color(base).b, 1)\n" +
            "}");
        assertEquals(128 / 255.0, ((Number) prop(root, "red")).doubleValue(), 1e-6);
        assertEquals(160 / 255.0, ((Number) prop(root, "green")).doubleValue(), 1e-6);
        assertEquals("#ff80a0c0", prop(root, "back"));
    }

    @Test
    void colorPropertyExposesChannelsDirectly() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "Item {\n" +
            "  property color c: \"#80a0c0\"\n" +
            "  property real cr: c.r\n" +
            "}");
        assertEquals(128 / 255.0, ((Number) prop(root, "cr")).doubleValue(), 1e-6);
    }
}
