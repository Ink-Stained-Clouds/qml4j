package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.engine.js.RhinoBinding;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Phase 6 prep: a binding that reads a QML singleton runs on Rhino. The singleton's
// SPECIFIC generated class is threaded into the binding (not a global name lookup), so
// it resolves correctly and stays reactive.
class RhinoSingletonTest {

    private static Object bindingOf(Property<?> p) throws Exception {
        Field f = Property.class.getDeclaredField("binding");
        f.setAccessible(true);
        return f.get(p);
    }

    private static void flush(QmlView v) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
    }

    @Test
    void singletonBindingRunsOnRhinoAndStaysReactive() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put("app/qmldir", "singleton Tokens 1.0 Tokens.qml".getBytes(StandardCharsets.UTF_8));
        files.put("app/Tokens.qml",
            "import QtQuick\nItem { property int gap: 100 }".getBytes(StandardCharsets.UTF_8));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import app\n" +
            "Rectangle { width: Tokens.gap + 5 }");
        flush(v);

        assertTrue(bindingOf(root.width) instanceof RhinoBinding,
            "singleton binding should be a RhinoBinding");
        assertEquals(105, root.width.peek().intValue());   // Tokens.gap (100) + 5
    }
}
