package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Probe: load MD3 TopAppBar.qml (real Layouts usage) with a stub IconButton.
// Surfaces default-property + .data alias + Text.elide gaps.
class TopAppBarLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = TopAppBarLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsMd3TopAppBar() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "TopAppBar.qml", "IconButton.qml", "Ripple.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item {\n" +
            "  width: 640; height: 64\n" +
            "  TopAppBar { title: \"Inbox\" }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "TopAppBar should instantiate");
    }
}
