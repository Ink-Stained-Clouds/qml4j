package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The MD3 NavigationRail item centres its indicator pill (anchors.horizontalCenter)
// and overlays the Ripple with `anchors.fill: indicator`. The Ripple must land on the
// centred pill, not at the item's left edge -- otherwise the ripple wave appears
// offset to the left of the pill.
class AnchorsFillCenteredSiblingTest {

    @Test
    void fillFollowsCenteredSiblingX() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: railItem\n"
            + "  width: 80; height: 56\n"
            + "  Rectangle {\n"
            + "    id: indicator\n"
            + "    width: 56; height: 32\n"
            + "    anchors.horizontalCenter: parent.horizontalCenter\n"
            + "  }\n"
            + "  Item {\n"
            + "    id: ripple\n"
            + "    anchors.fill: indicator\n"
            + "  }\n"
            + "}");
        root.width.set(80.0);
        root.height.set(56.0);
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { new Renderer().layoutOnly(root); } finally { dq.uninstall(); }

        Item indicator = root.children.get(0);
        Item ripple = root.children.get(1);
        // indicator centred in 80: x = (80 - 56) / 2 = 12
        assertEquals(12.0, indicator.x.peek().doubleValue(), 1e-6, "indicator centred");
        assertEquals(12.0, ripple.x.peek().doubleValue(), 1e-6, "ripple fills indicator, same x");
        assertEquals(56.0, ripple.width.peek().doubleValue(), 1e-6, "ripple matches indicator width");
    }
}
