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

// Probe: real MD3 Switch + RadioButton (RowLayout + Ripple + Behaviors) load.
class SwitchRadioLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = SwitchRadioLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Item load(String type) {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Ripple.qml", "Switch.qml", "RadioButton.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item { width: 200; height: 60\n" +
            "  " + type + " { text: \"Opt\"; checked: true }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        return root;
    }

    @Test
    void loadsSwitch() {
        assertFalse(load("Switch").children.isEmpty());
    }

    @Test
    void loadsRadioButton() {
        assertFalse(load("RadioButton").children.isEmpty());
    }
}
