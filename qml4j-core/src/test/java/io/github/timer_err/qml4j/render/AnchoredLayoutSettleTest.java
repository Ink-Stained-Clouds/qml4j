package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A container is laid out before its own box is known: children are measured bottom-up, so an
// anchored container's size only lands in the anchor pass that closes its own measure() -- after
// the layout() that already handed a stale width to its fill children. The correction has to be
// carried down within the same pass; leaving it to the next settle pass moved it one level per
// pass, which a static scene never got (no dirty binding, no second pass) and a deep chain ran
// out of passes for. This is the MD3 TextField shape: RowLayout at `anchors.fill: parent`.
class AnchoredLayoutSettleTest {

    private static Item layoutScene(String container) {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "import QtQuick.Layouts\n" +
            "Item { width: 400; height: 200\n" +
            "  Rectangle { id: box; width: 280; height: 56\n" +
            "    " + container + " {\n" +
            "      anchors.fill: parent\n" +
            "      anchors.leftMargin: 16\n" +
            "      anchors.rightMargin: 16\n" +
            "      Item { id: cell; Layout.fillWidth: true; Layout.fillHeight: true\n" +
            "        Rectangle { id: inner; anchors.fill: parent; color: \"#ff0000\" }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            dq.flush();
        } finally {
            dq.uninstall();
        }
        v.renderer().layoutOnly(root);
        return root;
    }

    private static Item cellOf(Item root) {
        return root.children.get(0).children.get(0).children.get(0);
    }

    @Test
    void fillWidthChildFillsAnAnchoredRowLayout() {
        Item cell = cellOf(layoutScene("RowLayout"));
        assertEquals(248f, cell.width.peekFloat(), "280 minus the layout's 16px side margins");
        assertEquals(56f, cell.height.peekFloat());
    }

    @Test
    void anchorsFillReachesTheGrandchild() {
        Item cell = cellOf(layoutScene("RowLayout"));
        Item inner = cell.children.get(0);
        assertEquals(248f, inner.width.peekFloat(), "anchors.fill against the settled cell");
        assertEquals(56f, inner.height.peekFloat());
    }

    @Test
    void fillWidthChildFillsAnAnchoredColumnLayout() {
        Item cell = cellOf(layoutScene("ColumnLayout"));
        assertEquals(248f, cell.width.peekFloat());
        assertEquals(56f, cell.height.peekFloat());
    }

    // Depth is the point: while the correction travelled one level per settle pass, this failed
    // silently past the pass cap AND burned the full cap doing it. One pass has to settle any
    // depth.
    @Test
    void nestedAnchoredLayoutsSettleInOnePass() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        int depth = 10;
        StringBuilder qml = new StringBuilder(
            "import QtQuick\n" +
            "import QtQuick.Layouts\n" +
            "Item { width: 400; height: 200\n" +
            "  Rectangle { width: 280; height: 56\n");
        for (int i = 0; i < depth; i++) {
            qml.append("RowLayout { anchors.fill: parent\n")
               .append("Item { Layout.fillWidth: true; Layout.fillHeight: true\n");
        }
        for (int i = 0; i < depth; i++) qml.append("} }\n");
        qml.append("} }\n");

        Item root = v.load(qml.toString());
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            dq.flush();
        } finally {
            dq.uninstall();
        }
        v.renderer().layoutOnly(root);

        Item deepest = root.children.get(0);
        for (int i = 0; i < depth; i++) deepest = deepest.children.get(0).children.get(0);
        assertEquals(280f, deepest.width.peekFloat(), "the innermost cell fills all 10 levels");
        assertEquals(1, v.renderer().settlePassCount(), "settled without a second pass");
    }
}
