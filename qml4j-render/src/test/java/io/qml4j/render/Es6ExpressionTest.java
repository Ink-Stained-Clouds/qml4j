package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Rectangle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Es6ExpressionTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    // ---- Template literals ----

    @Test
    void templateLiteralWithoutInterpolation() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property var msg: `hello world`\n" +
            "}");
        assertEquals("hello world", readProp(root, "msg"));
    }

    @Test
    void templateLiteralInterpolatesIdentifier() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property int n: 7\n" +
            "  property var msg: `n = ${n}`\n" +
            "}");
        assertEquals("n = 7", readProp(root, "msg"));
    }

    @Test
    void templateLiteralInterpolatesExpression() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property int a: 3\n" +
            "  property int b: 4\n" +
            "  property var msg: `${a} + ${b} = ${a + b}`\n" +
            "}");
        assertEquals("3 + 4 = 7", readProp(root, "msg"));
    }

    @Test
    void templateLiteralEscapeSequences() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property var msg: `line1\\nline2\\t\\`quoted\\``\n" +
            "}");
        assertEquals("line1\nline2\t`quoted`", readProp(root, "msg"));
    }

    @Test
    void templateLiteralIsReactive() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property int n: 1\n" +
            "  property var msg: `value=${n}`\n" +
            "}");
        assertEquals("value=1", readProp(root, "msg"));
        callSetProp(root, "n", 42L);
        assertEquals("value=42", readProp(root, "msg"));
    }

    @Test
    void templateLiteralNumberFormatting() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property var a: `${1 + 2}`\n" +
            "  property var b: `${1.5 + 2.5}`\n" +
            "}");
        assertEquals("3", readProp(root, "a"));
        assertEquals("4", readProp(root, "b"));
    }

    // ---- Spread ----

    @Test
    void spreadInArrayLiteral() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property var head: [1, 2]\n" +
            "  property var tail: [4, 5]\n" +
            "  property var all: [0, ...head, 3, ...tail, 6]\n" +
            "}");
        Object all = readProp(root, "all");
        assertTrue(all instanceof List);
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L), all);
    }

    @Test
    void spreadIntoMethodCall() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  id: r\n" +
            "  property var items: [10, 20, 30]\n" +
            "  property var sum: r.totalOf(...items)\n" +
            "  function totalOf(a, b, c) { return a + b + c; }\n" +
            "}");
        assertEquals(60L, readProp(root, "sum"));
    }

    @Test
    void spreadIntoRootFunctionBareCall() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  function totalOf(a, b, c) { return a + b + c; }\n" +
            "  property var items: [10, 20, 30]\n" +
            "  property var sum: totalOf(...items)\n" +
            "}");
        assertEquals(60L, readProp(root, "sum"));
    }

    // ---- Arrow functions ----

    @Test
    void arrowFunctionSinglePararmIdentityCall() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  function tryIt() {\n" +
            "    let f = x => x * 2;\n" +
            "    return f(7);\n" +
            "  }\n" +
            "}");
        assertEquals(14L, invoke(root, "tryIt"));
    }

    @Test
    void arrowFunctionMultipleParamsAndBlock() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  function tryIt() {\n" +
            "    let add = (a, b) => { return a + b; };\n" +
            "    return add(3, 4);\n" +
            "  }\n" +
            "}");
        assertEquals(7L, invoke(root, "tryIt"));
    }

    @Test
    void arrowFunctionReadsOuterProperty() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property int factor: 5\n" +
            "  function tryIt() {\n" +
            "    let scale = n => n * factor;\n" +
            "    return scale(3);\n" +
            "  }\n" +
            "}");
        assertEquals(15L, readResult(root, "tryIt"));
        callSetProp(root, "factor", 10L);
        assertEquals(30L, readResult(root, "tryIt"));
    }

    @Test
    void arrowFunctionZeroParams() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  function tryIt() {\n" +
            "    let mk = () => 42;\n" +
            "    return mk();\n" +
            "  }\n" +
            "}");
        assertEquals(42L, invoke(root, "tryIt"));
    }

    @Test
    void arrowFunctionStoredInProperty() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  property var doubler: x => x * 2\n" +
            "}");
        Object f = readProp(root, "doubler");
        assertTrue(f instanceof io.qml4j.engine.Callable, "expected Callable, got " + f);
        Object out = ((io.qml4j.engine.Callable) f).call(new Object[]{6L});
        assertEquals(12L, out);
    }

    // ---- helpers ----

    private static Object invoke(Object o, String name) {
        try {
            for (Method m : o.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m.invoke(o);
            }
            throw new IllegalArgumentException("no method " + name);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Object readResult(Object o, String name) {
        return invoke(o, name);
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void callSetProp(Object o, String name, Object v) {
        try {
            Field f = o.getClass().getField(name);
            ((Property) f.get(o)).set(v);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
