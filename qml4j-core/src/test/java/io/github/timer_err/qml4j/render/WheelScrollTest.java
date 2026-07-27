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

    // A horizontal strip parked under the cursor must not swallow the vertical wheel of
    // the list it sits in.
    @Test
    void wheelFallsThroughAHorizontalChildToTheVerticalParent() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Flickable outer = (Flickable) load(v, nestedStrip(2000));
        Flickable strip = (Flickable) outer.children.get(0);

        assertTrue(v.dispatchWheel(150, 50, 0, -1));
        assertEquals(48.0, outer.contentY.peek().doubleValue(), 0.5);
        assertEquals(0.0, strip.contentX.peek().doubleValue(), 0.5);
    }

    // With no vertical room anywhere in the chain the notch still scrolls the strip.
    @Test
    void wheelScrollsAHorizontalStripWhenNothingScrollsVertically() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Flickable outer = (Flickable) load(v, nestedStrip(400));
        Flickable strip = (Flickable) outer.children.get(0);

        assertTrue(v.dispatchWheel(150, 50, 0, -1));
        assertEquals(48.0, strip.contentX.peek().doubleValue(), 0.5);
        assertEquals(0.0, outer.contentY.peek().doubleValue(), 0.5);
    }

    // A 300x400 vertical Flickable of the given content height, holding a horizontal
    // 300x100 strip of 900 content width at the top-left.
    private static String nestedStrip(int outerContentHeight) {
        return "import QtQuick\n"
            + "Flickable {\n"
            + "  width: 300; height: 400\n"
            + "  contentWidth: 300; contentHeight: " + outerContentHeight + "\n"
            + "  flickableDirection: \"VerticalFlick\"\n"
            + "  Flickable {\n"
            + "    x: 0; y: 0; width: 300; height: 100\n"
            + "    contentWidth: 900; contentHeight: 100\n"
            + "    flickableDirection: \"HorizontalFlick\"\n"
            + "  }\n"
            + "}\n";
    }
}
