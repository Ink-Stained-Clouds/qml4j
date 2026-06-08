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

// A string import is a path relative to the importing document's directory (Qt). The
// MD3 app uses `import "../../Core/Styles/animations"` from pages/, and
// `import "../widgets" as Widgets` -- `..` must escape the importing file's directory.
class RelativeImportTest {

    private static String prop(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return (String) ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void parentRelativeImportEscapesImportingDir() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Map<String, byte[]> files = new HashMap<>();
        // A shared type one level up from the importing page.
        files.put("shared/Badge.qml",
            "import QtQuick\nItem { property string label: \"badge\" }".getBytes());
        // The page lives in pages/ and reaches the sibling shared/ via `..`.
        files.put("pages/Home.qml",
            ("import QtQuick\nimport \"../shared\"\n"
             + "Item { property string read: badge.label\n"
             + "  Badge { id: badge } }").getBytes());
        v.resources(files::get);

        Item root = v.load(new String(files.get("pages/Home.qml")), "pages");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertEquals("badge", prop(root, "read"));
    }

    @Test
    void aliasedRelativeImportResolvesNamespacedType() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Map<String, byte[]> files = new HashMap<>();
        files.put("widgets/Clock.qml",
            "import QtQuick\nItem { property string face: \"tick\" }".getBytes());
        files.put("pages/Home.qml",
            ("import QtQuick\nimport \"../widgets\" as W\n"
             + "Item { property string read: c.face\n"
             + "  W.Clock { id: c } }").getBytes());
        v.resources(files::get);

        Item root = v.load(new String(files.get("pages/Home.qml")), "pages");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertEquals("tick", prop(root, "read"));
    }
}
