package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A QtObject-typed property NAMED `color` must hold the nested object, not be
// coerced to a color String. MD3 Theme uses Theme.color.outline.
class ColorPropNameTest {

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
    void qtObjectPropertyNamedColorHoldsNestedObject() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "Item {\n" +
            "  property QtObject color: QtObject {\n" +
            "    property color outline: \"#79747e\"\n" +
            "  }\n" +
            "  property string read: color.outline\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertEquals("#79747e", prop(root, "read"));
    }

    @Test
    void singletonThemeColorGroupHoldsNestedObject() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        files.put("md3/Core/qmldir", "singleton Theme 1.0 Theme.qml\n".getBytes());
        files.put("md3/Core/Theme.qml",
            ("pragma Singleton\n" +
             "import QtQuick\n" +
             "QtObject {\n" +
             "  property QtObject color: QtObject {\n" +
             "    property color outline: \"#79747e\"\n" +
             "  }\n" +
             "}").getBytes());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item { property string read: Theme.color.outline }");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertEquals("#79747e", prop(root, "read"));
    }

    // Two modules each export a singleton named Theme with different shapes.
    // Each importing component must resolve ITS module's Theme, not whichever
    // was cached first. (Device crash: md3 ScrollBar's Theme.color.outline hit
    // the flat M45 Theme cached under the bare name "Theme".)
    @Test
    void twoModulesWithSameSingletonNameDoNotCollide() {
        QmlView v = newView();
        Map<String, byte[]> files = new HashMap<>();
        // A string import is relative to the importing file's directory (Qt), so the
        // modules sit under comps/ next to the components that import them.
        files.put("comps/flat/qmldir", "singleton Theme 1.0 Theme.qml\n".getBytes());
        files.put("comps/flat/Theme.qml",
            ("pragma Singleton\nimport QtQuick\n" +
             "QtObject { property color primary: \"#112233\" }").getBytes());
        files.put("comps/nested/qmldir", "singleton Theme 1.0 Theme.qml\n".getBytes());
        files.put("comps/nested/Theme.qml",
            ("pragma Singleton\nimport QtQuick\n" +
             "QtObject { property QtObject color: QtObject { property color outline: \"#79747e\" } }").getBytes());
        files.put("comps/UsesFlat.qml",
            "import QtQuick\nimport \"flat\"\nItem { property string read: Theme.primary }".getBytes());
        files.put("comps/UsesNested.qml",
            "import QtQuick\nimport \"nested\"\nItem { property string read: Theme.color.outline }".getBytes());
        v.resources(files::get);

        // Resolve the flat Theme FIRST so it would poison a bare-name cache.
        Item root = v.load(
            "import QtQuick\n" +
            "import \"comps\"\n" +
            "Item { UsesFlat { id: a } UsesNested { id: b } }");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        Item a = root.children.get(0);
        Item b = root.children.get(1);
        assertEquals("#112233", prop(a, "read"));
        assertEquals("#79747e", prop(b, "read"));
    }
}
