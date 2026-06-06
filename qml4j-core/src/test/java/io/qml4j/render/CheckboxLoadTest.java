package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Acceptance: real MD3 Checkbox.qml (RowLayout + Ripple/MultiEffect + Qt.color
// + readonly) loads unmodified.
class CheckboxLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = CheckboxLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsMd3Checkbox() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Ripple.qml", "Checkbox.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item { width: 200; height: 60\n" +
            "  Checkbox { text: \"Enable\"; checked: true }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "Checkbox should instantiate");
    }
}
