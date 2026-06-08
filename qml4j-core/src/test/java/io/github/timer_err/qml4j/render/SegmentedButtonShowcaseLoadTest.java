package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// The launcher's SegmentedButton showcase loads end to end: it instantiates two real
// SegmentedButtons (for-in RhinoFunction + Repeater delegate bindings + an arrow-form
// onClicked handler), so a regression in any of those surfaces here.
class SegmentedButtonShowcaseLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = SegmentedButtonShowcaseLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsSegmentedButtonShowcase() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("md3/Core/qmldir", res("md3/Core/qmldir"));
        files.put("md3/Core/Theme.qml", res("md3/Core/Theme.qml"));
        files.put("md3/Core/Ripple.qml", res("md3/Core/Ripple.qml"));
        files.put("md3/Core/SegmentedButton.qml", res("md3/Core/SegmentedButton.qml"));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(new String(res("showcases/SegmentedButtonShowcase.qml"), StandardCharsets.UTF_8));

        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "showcase should instantiate");
    }
}
