package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingletonTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void pragmaSingletonReferencedByIdentifier() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("theme/Theme.qml",
            ("pragma Singleton\n" +
             "Rectangle { property color primary: \"#ff8800\" }").getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"theme\"\n" +
            "Rectangle { color: Theme.primary }");
        assertEquals("#ff8800", ((Rectangle) root).color.peek());
    }

    @Test
    void qmldirSingletonMarkerWorksWithoutPragma() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("theme/qmldir",
            "singleton Theme 1.0 Theme.qml\n".getBytes());
        files.put("theme/Theme.qml",
            "Rectangle { property int answer: 42 }".getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"theme\"\n" +
            "Rectangle { property int n: Theme.answer }");
        assertEquals(42L, readProp(root, "n"));
    }

    @Test
    void singletonInstanceIsSharedAcrossReferences() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("counter/Counter.qml",
            ("pragma Singleton\n" +
             "Rectangle { property int n: 0 }").getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import \"counter\"\n" +
            "Rectangle {\n" +
            "  property int a: Counter.n\n" +
            "  property int b: Counter.n\n" +
            "  function bump() { Counter.n = Counter.n + 1 }\n" +
            "}");
        assertEquals(0L, readProp(root, "a"));
        assertEquals(0L, readProp(root, "b"));
        invoke(root, "bump");
        v.dirtyQueue().install();
        try { v.dirtyQueue().flush(); }
        finally { v.dirtyQueue().uninstall(); }
        assertEquals(1L, readProp(root, "a"));
        assertEquals(1L, readProp(root, "b"));
    }

    @Test
    void singletonConstructionAsChildObjectRejected() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("theme/Theme.qml",
            ("pragma Singleton\n" +
             "Rectangle { property color primary: \"#abc\" }").getBytes());
        v.resources(files::get);
        assertThrows(IllegalArgumentException.class, () ->
            v.load(
                "import \"theme\"\n" +
                "Item { Theme { } }"));
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Object invoke(Object o, String name) {
        try {
            for (java.lang.reflect.Method m : o.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m.invoke(o);
            }
            throw new IllegalArgumentException("no method " + name);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
