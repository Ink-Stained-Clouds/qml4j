package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A closure built inside a binding (ComboBox builds per-item `action` closures that
// capture the enclosing `control` id) must, when invoked later from a different
// scope, still mutate the defining-scope id -- not the calling scope's same-named id.
class ClosureCaptureTest {

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void closureMutatesDefiningScopeId() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: control\n"
            + "  property int currentIndex: -1\n"
            + "  property var actions: {\n"
            + "    var m = []\n"
            + "    for (var i = 0; i < 3; i++) {\n"
            + "      m.push(function(idx){ return function(){ control.currentIndex = idx } }(i))\n"
            + "    }\n"
            + "    return m\n"
            + "  }\n"
            + "  function fire(n) { actions[n]() }\n"
            + "}");
        // invoke action[2] via the QML function (mimics Ripple.onClicked calling itemData.action())
        invoke(root, "fire", 2);
        assertEquals(2L, ((Number) readProp(root, "currentIndex")).longValue());
    }

    @Test
    void directAssignFromFunction() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: control\n"
            + "  property int currentIndex: -1\n"
            + "  function set(n) { control.currentIndex = n }\n"
            + "}");
        invoke(root, "set", 2);
        assertEquals(2L, ((Number) readProp(root, "currentIndex")).longValue());
    }

    @Test
    void nestedClosureNoIife() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: control\n"
            + "  property int currentIndex: -1\n"
            + "  property var mk: (function(){ return function(){ control.currentIndex = 5 } })()\n"
            + "  function fire() { mk() }\n"
            + "}");
        invoke0(root, "fire");
        assertEquals(5L, ((Number) readProp(root, "currentIndex")).longValue());
    }

    @Test
    void closureSeesControl() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: control\n"
            + "  property var mk: (function(){ return function(){ return typeof control } })()\n"
            + "  function probe() { return mk() }\n"
            + "}");
        Object r = invoke0(root, "probe");
        assertEquals("object", r, "control visible inside nested closure");
    }

    private static Object invoke0(Item root, String fn) {
        try {
            for (java.lang.reflect.Method m : root.getClass().getMethods()) {
                if (m.getName().equals(fn) && m.getParameterCount() == 0) { return m.invoke(root); }
            }
            throw new NoSuchMethodException(fn);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void invoke(Item root, String fn, Object arg) {
        try {
            for (java.lang.reflect.Method m : root.getClass().getMethods()) {
                if (m.getName().equals(fn) && m.getParameterCount() == 1) {
                    m.invoke(root, arg);
                    return;
                }
            }
            throw new NoSuchMethodException(fn);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
