package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QtNamespaceTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void rgbaFormatsHexWithAlpha() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  color: Qt.rgba(1.0, 0.0, 0.0, 1.0)\n" +
            "}");
        assertEquals("#ffff0000", ((Rectangle) root).color.peek());
    }

    @Test
    void rgbaSupportsTranslucent() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  color: Qt.rgba(0.0, 0.5, 1.0, 0.5)\n" +
            "}");
        String c = (String) ((Rectangle) root).color.peek();
        assertEquals("#800080ff", c);
    }

    @Test
    void hslaProducesExpectedPrimary() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  color: Qt.hsla(0.0, 1.0, 0.5, 1.0)\n" +
            "}");
        assertEquals("#ffff0000", ((Rectangle) root).color.peek());
    }

    @Test
    void hslaGreenAtOneThird() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  color: Qt.hsla(0.3333, 1.0, 0.5, 1.0)\n" +
            "}");
        String c = (String) ((Rectangle) root).color.peek();
        assertTrue(c.startsWith("#ff00f"), "expected green-ish, got " + c);
    }

    @Test
    void qtBindingRestoresReactivityAfterImperativeWrite() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  id: r\n" +
            "  property int factor: 2\n" +
            "  property int derived: factor * 10\n" +
            "  function rebind() { r.derived = Qt.binding(r.factor * 10); }\n" +
            "}");
        assertEquals(20L, readProp(root, "derived"));
        callSetProp(root, "derived", 999L);
        assertEquals(999L, readProp(root, "derived"));
        callSetProp(root, "factor", 5L);
        assertEquals(999L, readProp(root, "derived"));
        invoke(root, "rebind");
        flushDirty(v);
        assertEquals(50L, readProp(root, "derived"));
        callSetProp(root, "factor", 7L);
        flushDirty(v);
        assertEquals(70L, readProp(root, "derived"));
    }

    @Test
    void callLaterDefersWorkUntilFlush() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  id: r\n" +
            "  property int hits: 0\n" +
            "  function bump() { Qt.callLater(Qt.binding(r.hits = r.hits + 1)); }\n" +
            "}");
        v.dirtyQueue().install();
        try {
            invoke(root, "bump");
            assertEquals(0L, readProp(root, "hits"));
            v.dirtyQueue().flush();
            assertEquals(1L, readProp(root, "hits"));
        } finally {
            v.dirtyQueue().uninstall();
        }
    }

    private static Object invoke(Object o, String name) {
        try {
            for (java.lang.reflect.Method m : o.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    return m.invoke(o);
                }
            }
            throw new IllegalArgumentException("no method " + name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object readProp(Object o, String name) {
        try {
            java.lang.reflect.Field f = o.getClass().getField(name);
            return ((io.qml4j.engine.binding.Property<?>) f.get(o)).peek();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void callSetProp(Object o, String name, Object v) {
        try {
            java.lang.reflect.Field f = o.getClass().getField(name);
            ((io.qml4j.engine.binding.Property) f.get(o)).set(v);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void flushDirty(QmlView v) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); }
        finally { dq.uninstall(); }
    }
}
