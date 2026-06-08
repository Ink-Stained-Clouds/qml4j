package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The MD3 SideSheet starts invisible; its panel parks off-screen-right via
// `x: parent.width`. If an invisible subtree is never laid out, parent.width stays 0
// and the panel parks at x=0 (left) -- so the first open slides in from the LEFT, and
// only after a close (which sets x = parent.width) does the second open slide from the
// right. The panel must park at parent.width even while the sheet is invisible.
class InvisibleSubtreeLayoutTest {

    @Test
    void invisiblePanelParksAtParentWidth() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: page\n"
            + "  width: 460; height: 800\n"
            + "  Item {\n"
            + "    id: sheet\n"
            + "    anchors.fill: parent\n"
            + "    visible: false\n"
            + "    Rectangle {\n"
            + "      id: panel\n"
            + "      width: 320; height: 800\n"
            + "      x: parent.width\n"
            + "    }\n"
            + "  }\n"
            + "}");
        root.width.set(460.0);
        root.height.set(800.0);
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { new Renderer().layoutOnly(root); } finally { dq.uninstall(); }

        Item sheet = root.children.get(0);
        Item panel = sheet.children.get(0);
        assertEquals(460.0, sheet.width.peek().doubleValue(), 1e-6, "invisible sheet still fills parent");
        assertEquals(460.0, panel.x.peek().doubleValue(), 1e-6,
            "panel parks off-screen-right (parent.width), not at left edge");
    }
}
