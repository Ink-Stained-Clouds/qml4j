package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BehaviorAliasToggleTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // Root cause of the demo "tap me" dot freezing on the 3rd tap: a Behavior/
    // NumberAnimation tweens an int property to a Double end value, then the
    // handler's `value === 400` (strict-equals an int literal) wrongly returns
    // false because eqStrict compared Java box classes (Double != Long). JS/QML
    // has one number type: 400.0 === 400 must be true.
    @Test
    void strictEqualsIsValueBasedForNumbers() {
        Item root = newView().load(
            "Item {\n" +
            "  property real f: 400.0\n" +
            "  property bool a: f === 400\n" +      // double prop === int literal
            "  property bool b: 400 === 400.0\n" +  // int literal === double literal
            "  property bool c: f !== 400\n" +
            "  property bool d: f === 401\n" +
            "}");
        assertEquals(Boolean.TRUE, readProp(root, "a"));
        assertEquals(Boolean.TRUE, readProp(root, "b"));
        assertEquals(Boolean.FALSE, readProp(root, "c"));
        assertEquals(Boolean.FALSE, readProp(root, "d"));
    }

    // The exact toggle pattern: a value that has become a Double (as a tween
    // would leave it) still compares === to its original int, so the toggle
    // keeps alternating instead of sticking.
    @Test
    void toggleComparisonSurvivesDoublePromotion() {
        Item root = newView().load(
            "Item {\n" +
            "  property var pos: 400.0\n" +
            "  property int flips: 0\n" +
            "  function tap() {\n" +
            "    var atLeft = pos === 400;\n" +
            "    if (atLeft) pos = 800.0; else pos = 400.0;\n" +
            "    flips = flips + 1;\n" +
            "  }\n" +
            "}");
        invoke(root, "tap");
        assertEquals(800.0, ((Number) readProp(root, "pos")).doubleValue(), 1e-6, "1st -> 800");
        invoke(root, "tap");
        assertEquals(400.0, ((Number) readProp(root, "pos")).doubleValue(), 1e-6, "2nd -> 400");
        invoke(root, "tap");
        assertEquals(800.0, ((Number) readProp(root, "pos")).doubleValue(), 1e-6, "3rd -> 800 (the bug)");
    }

    private static void invoke(Object o, String name) {
        try {
            for (java.lang.reflect.Method m : o.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) { m.invoke(o); return; }
            }
            throw new IllegalArgumentException("no method " + name);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
