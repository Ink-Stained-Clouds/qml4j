package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Flickable;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Mouse-wheel scrolling: a notch moves the Flickable under the cursor by WHEEL_STEP,
// clamped to [0, contentHeight - height].
class WheelScrollTest {

    private static Item load(QmlView v, String qml) {
        Item root = v.load(qml);
        root.width.set(300.0);
        root.height.set(400.0);
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        return root;
    }

    @Test
    void wheelScrollsAndClamps() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Flickable f = (Flickable) load(v,
            "import QtQuick\n"
            + "Flickable {\n"
            + "  width: 300; height: 400\n"
            + "  contentWidth: 300; contentHeight: 2000\n"
            + "  Rectangle { width: 300; height: 2000 }\n"
            + "}\n");

        // One notch down (GLFW yoffset -1) moves content down by WHEEL_STEP (48).
        assertTrue(v.dispatchWheel(150, 200, 0, -1));
        assertEquals(48.0, f.contentY.peek().doubleValue(), 0.5);

        // Far past the bottom clamps to contentHeight - height = 1600.
        for (int i = 0; i < 100; i++) v.dispatchWheel(150, 200, 0, -1);
        assertEquals(1600.0, f.contentY.peek().doubleValue(), 0.5);

        // Scrolling back up clamps at the top.
        for (int i = 0; i < 100; i++) v.dispatchWheel(150, 200, 0, 1);
        assertEquals(0.0, f.contentY.peek().doubleValue(), 0.5);
    }

    @Test
    void wheelOutsideAnyFlickableIsIgnored() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        load(v, "import QtQuick\nRectangle { width: 300; height: 400 }\n");
        assertTrue(!v.dispatchWheel(150, 200, 0, -1));
    }
}
