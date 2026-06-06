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

// Phase 3 capstone: the MD3 SegmentedButton loads. Its _handleClicked uses for-in
// (and i++), which the ANTLR grammar rejected and the ASM codegen could not lower --
// the original motivation for the Rhino migration. With the for-in grammar + the
// RhinoFunction backend it parses, compiles, and instantiates.
class SegmentedButtonLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = SegmentedButtonLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsMd3SegmentedButtonUnmodified() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("md3/Core/qmldir", res("md3/Core/qmldir"));
        files.put("md3/Core/Theme.qml", res("md3/Core/Theme.qml"));
        files.put("md3/Core/Ripple.qml", res("md3/Core/Ripple.qml"));
        files.put("md3/Core/SegmentedButton.qml", res("md3/Core/SegmentedButton.qml"));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item {\n" +
            "  width: 400; height: 60\n" +
            "  SegmentedButton { id: seg }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "SegmentedButton should instantiate");
    }
}
