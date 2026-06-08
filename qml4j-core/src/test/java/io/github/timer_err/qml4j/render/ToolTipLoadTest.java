package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Next acceptance probe: load the unmodified MD3 ToolTip.qml. Surfaces the next
// gap batch (deep theme groups, font.* group props, Animation/Timer methods).
class ToolTipLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = ToolTipLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void loadsMd3ToolTipUnmodified() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("md3/Core/qmldir", res("md3/Core/qmldir"));
        files.put("md3/Core/Theme.qml", res("md3/Core/Theme.qml"));
        files.put("md3/Core/ToolTip.qml", res("md3/Core/ToolTip.qml"));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item {\n" +
            "  width: 300; height: 200\n" +
            "  ToolTip { id: tip; text: \"hello\" }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "ToolTip should instantiate");

        // open() calls showAnim.restart() + hideTimer.restart(): exercises the
        // Animation/Timer methods. After ticking, opacity should rise toward 1.
        Item tip = root.children.get(0);
        invoke(tip, "open");
        long t = 1_000_000_000L;
        for (int i = 0; i < 8; i++) {
            t += 16_000_000L;
            dq.install();
            try { v.tickAnimations(t); dq.flush(); } finally { dq.uninstall(); }
        }
        double op = ((Number) tip.opacity.peek()).doubleValue();
        assertTrue(op > 0.5, "ToolTip should be fading in, opacity was " + op);
    }

    private static void invoke(Object o, String name) {
        try {
            for (Method m : o.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) { m.invoke(o); return; }
            }
            throw new IllegalArgumentException("no method " + name);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
