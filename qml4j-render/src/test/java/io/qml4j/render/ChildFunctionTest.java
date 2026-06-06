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
    void bareNameInChildBindingFindsChildFunction() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  Rectangle {\n" +
            "    id: inner\n" +
            "    function localDouble(n) { return n * 2; }\n" +
            "    property var out: localDouble(7)\n" +
            "  }\n" +
            "  property var mirror: inner.out\n" +
            "}");
        assertEquals(14L, readProp(root, "mirror"));
    }

    @Test
    void bareNameInChildBindingWalksToRoot() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  function rootHelper(x) { return x + 100; }\n" +
            "  Rectangle {\n" +
            "    id: inner\n" +
            "    property var out: rootHelper(5)\n" +
            "  }\n" +
            "  property var mirror: inner.out\n" +
            "}");
        assertEquals(105L, readProp(root, "mirror"));
    }

    @Test
    void childFunctionShadowsRootFunction() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  function pick() { return 1; }\n" +
            "  Rectangle {\n" +
            "    id: inner\n" +
            "    function pick() { return 2; }\n" +
            "    property var out: pick()\n" +
            "  }\n" +
            "  property var mine: pick()\n" +
            "  property var theirs: inner.out\n" +
            "}");
        assertEquals(1L, readProp(root, "mine"));
        assertEquals(2L, readProp(root, "theirs"));
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
