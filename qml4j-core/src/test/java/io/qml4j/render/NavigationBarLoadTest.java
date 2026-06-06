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

// Probe: real MD3 NavigationBar (StackLayout content + Repeater of nav items with
// selected-pill State.when/Transition, default-property content alias) load.
class NavigationBarLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = NavigationBarLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Item load(String body) {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Ripple.qml", "NavigationBar.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item { width: 400; height: 600\n" +
            "  " + body + "\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        return root;
    }

    @Test
    void loadsNavigationBar() {
        assertFalse(load(
            "NavigationBar {\n" +
            "  anchors.fill: parent\n" +
            "  currentIndex: 1\n" +
            "  model: [ {icon:\"home\", text:\"Home\"}, {icon:\"search\", text:\"Search\"} ]\n" +
            "  Rectangle { color: \"#112233\" }\n" +
            "  Rectangle { color: \"#334455\" }\n" +
            "}").children.isEmpty());
    }
}
