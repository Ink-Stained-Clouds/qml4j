package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChildFunctionTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void childObjectFunctionCalledByMember() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  Rectangle {\n" +
            "    id: inner\n" +
            "    function add(a, b) { return a + b; }\n" +
            "  }\n" +
            "  property var sum: inner.add(2, 3)\n" +
            "}");
        assertEquals(5L, readProp(root, "sum"));
    }

    @Test
    void childFunctionReadsOwnProperty() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  Rectangle {\n" +
            "    id: inner\n" +
            "    property int base: 7\n" +
            "    function scaled(k) { return base * k; }\n" +
            "  }\n" +
            "  property var out: inner.scaled(6)\n" +
            "}");
        assertEquals(42L, readProp(root, "out"));
    }

    @Test
    void childFunctionWithSpreadArgs() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  Rectangle {\n" +
            "    id: inner\n" +
            "    function totalOf(a, b, c) { return a + b + c; }\n" +
            "  }\n" +
            "  property var nums: [4, 5, 6]\n" +
            "  property var sum: inner.totalOf(...nums)\n" +
            "}");
        assertEquals(15L, readProp(root, "sum"));
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
