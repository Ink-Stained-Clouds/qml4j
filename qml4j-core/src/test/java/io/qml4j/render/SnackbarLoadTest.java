package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Probe: real MD3 Snackbar (RowLayout + Button contentItem delegate + Ripple +
// show/hide NumberAnimations + Timer) load.
class SnackbarLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = SnackbarLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Item load(String body) {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Ripple.qml", "Button.qml", "Snackbar.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item { width: 400; height: 200\n" +
            "  " + body + "\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        return root;
    }

    @Test
    void loadsSnackbarWithAction() {
        assertFalse(load("Snackbar { text: \"Saved\"; actionText: \"UNDO\" }").children.isEmpty());
    }

    @Test
    void loadsSnackbarWithCloseIcon() {
        assertFalse(load("Snackbar { text: \"Deleted\" }").children.isEmpty());
    }
}
