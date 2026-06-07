package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.layout.ColumnLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// MD3 NavigationRail items carry `Behavior on Layout.preferredHeight`. On load the
// height binding settles from its default to the real value; a Behavior must NOT
// animate that initial assignment (Qt does not), else every item grows from 0 and
// the whole column slides down from the first item's position into place.
class BehaviorPreferredSizeStartupTest {

    private static final String SRC =
        "import QtQuick\n"
        + "import QtQuick.Layouts\n"
        + "Item {\n"
        + "  ColumnLayout {\n"
        + "    id: col\n"
        + "    width: 80\n"
        + "    spacing: 0\n"
        + "    Repeater {\n"
        + "      model: 3\n"
        + "      Item {\n"
        + "        Layout.fillWidth: true\n"
        // height depends on a sibling defined LATER (like the rail's itemLabel.visible),
        // so the real value arrives after the Behavior has attached -- the case that
        // animates the initial assignment.
        + "        Layout.preferredHeight: 56 + (lbl.visible ? 20 : 0)\n"
        + "        Behavior on Layout.preferredHeight { NumberAnimation { duration: 200 } }\n"
        + "        Item { id: lbl; visible: true }\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}";

    private long clock = 6_000_000_000L;

    private void frame(QmlView v, Item root, long deltaMs) {
        clock += deltaMs * 1_000_000L;
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            new Renderer().layoutOnly(root);
            v.tickAnimations(clock);
            dq.flush();
        } finally {
            dq.uninstall();
        }
    }

    @Test
    void railItemsStartAtTheirPositionsNotStacked() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(SRC);
        root.width.set(80.0);
        root.height.set(400.0);

        // First couple of frames after load: the items must already be at their own
        // y (76 apart), not all stacked at 0 and animating apart.
        frame(v, root, 1);
        frame(v, root, 16);

        ColumnLayout col = (ColumnLayout) root.children.get(0);
        // children[0] is the Repeater node; the 3 delegates follow.
        Item item1 = col.children.get(2);
        Item item2 = col.children.get(3);
        assertEquals(76.0, item1.y.peek().doubleValue(), 1.0, "2nd item at y=76, not stacked at top");
        assertEquals(152.0, item2.y.peek().doubleValue(), 1.0, "3rd item at y=152");
    }
}
