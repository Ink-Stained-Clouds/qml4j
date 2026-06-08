package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void qtOrientationEnums() {
        Item root = newView().load(
            "Item {\n" +
            "  property int v: Qt.Vertical\n" +
            "  property int h: Qt.Horizontal\n" +
            "  property bool isV: Qt.Vertical === 2\n" +
            "}");
        assertEquals(2L, readProp(root, "v"));
        assertEquals(1L, readProp(root, "h"));
        assertEquals(Boolean.TRUE, readProp(root, "isV"));
    }

    @Test
    void qtAlignmentEnums() {
        Item root = newView().load(
            "Item {\n" +
            "  property int vc: Qt.AlignVCenter\n" +
            "  property int hc: Qt.AlignHCenter\n" +
            "  property int c: Qt.AlignCenter\n" +
            "}");
        assertEquals(128L, readProp(root, "vc"));
        assertEquals(4L, readProp(root, "hc"));
        assertEquals(132L, readProp(root, "c"));
    }

    @Test
    void textEnums() {
        Item root = newView().load(
            "Item {\n" +
            "  property int al: Text.AlignVCenter\n" +
            "  property int wr: Text.WordWrap\n" +
            "  property int el: Text.ElideRight\n" +
            "}");
        assertEquals(128L, readProp(root, "al"));
        assertEquals(1L, readProp(root, "wr"));
        assertEquals(3L, readProp(root, "el"));
    }

    @Test
    void fontEnums() {
        Item root = newView().load(
            "Item {\n" +
            "  property int n: Font.Normal\n" +
            "  property int b: Font.Bold\n" +
            "  property int m: Font.Medium\n" +
            "}");
        assertEquals(50L, readProp(root, "n"));
        assertEquals(75L, readProp(root, "b"));
        assertEquals(57L, readProp(root, "m"));
    }

    @Test
    void easingEnums() {
        Item root = newView().load(
            "Item {\n" +
            "  property int lin: Easing.Linear\n" +
            "  property int oq: Easing.OutQuad\n" +
            "}");
        assertEquals(0L, readProp(root, "lin"));
        assertEquals(2L, readProp(root, "oq"));
    }

    @Test
    void enumNamespaceNotShadowedByProperty() {
        // a property named like an enum member resolves normally, not as enum
        Item root = newView().load(
            "Item {\n" +
            "  property int Vertical: 99\n" +
            "  property int read: Vertical\n" +
            "}");
        assertEquals(99L, readProp(root, "read"));
    }
}
