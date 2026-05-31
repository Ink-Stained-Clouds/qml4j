package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Acceptance probe: load a real third-party MD3 component (ScrollBar.qml)
// UNMODIFIED, with only a hand-written Theme stub, via `import md3.Core`.
class ScrollBarLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = ScrollBarLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsMd3ScrollBarUnmodified() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("md3/Core/qmldir", res("md3/Core/qmldir"));
        files.put("md3/Core/Theme.qml", res("md3/Core/Theme.qml"));
        files.put("md3/Core/ScrollBar.qml", res("md3/Core/ScrollBar.qml"));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item {\n" +
            "  width: 100; height: 400\n" +
            "  Flickable { id: fl; contentWidth: 100; contentHeight: 2000 }\n" +
            "  ScrollBar { target: fl }\n" +
            "}");
        io.qml4j.engine.binding.DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "ScrollBar should instantiate");
    }

    // Mirrors android-shell ScrollBarShowcase.qml: explicit geometry + orientation
    // + a sibling-id binding, the way the device demo wires it.
    @Test
    void loadsScrollBarShowcaseLayout() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("md3/Core/qmldir", res("md3/Core/qmldir"));
        files.put("md3/Core/Theme.qml", res("md3/Core/Theme.qml"));
        files.put("md3/Core/ScrollBar.qml", res("md3/Core/ScrollBar.qml"));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Rectangle {\n" +
            "  width: 880; height: 320; color: \"#1c1c28\"\n" +
            "  Flickable { id: fl; x: 16; y: 56; width: 420; height: 240\n" +
            "    contentWidth: 420; contentHeight: 2000; clip: true }\n" +
            "  ScrollBar { target: fl; orientation: Qt.Vertical\n" +
            "    x: fl.x + fl.width + 2; y: fl.y; height: fl.height }\n" +
            "}");
        assertFalse(root.children.isEmpty(), "showcase should instantiate");
    }
}
