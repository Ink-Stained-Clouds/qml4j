package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.layout.ColumnLayout;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.layout.RowLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// QtQuick.Layouts RowLayout/ColumnLayout + Layout.* attached properties.
class LayoutTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void rowLayoutPositionsWithMarginsAndSpacing() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "import QtQuick.Layouts\n" +
            "Item {\n" +
            "  RowLayout {\n" +
            "    id: rl\n" +
            "    spacing: 8\n" +
            "    Item { implicitWidth: 48; implicitHeight: 48 }\n" +
            "    Item { implicitWidth: 30; implicitHeight: 20\n" +
            "      Layout.leftMargin: 4; Layout.rightMargin: 16 }\n" +
            "  }\n" +
            "}");
        RowLayout rl = (RowLayout) root.children.get(0);
        rl.layout();
        Item a = rl.children.get(0);
        Item b = rl.children.get(1);
        assertEquals(0.0, a.x.peek().doubleValue(), 1e-6);
        assertEquals(48.0, a.width.peek().doubleValue(), 1e-6);
        // b: after a(48) + spacing(8) + leftMargin(4)
        assertEquals(60.0, b.x.peek().doubleValue(), 1e-6);
        assertEquals(30.0, b.width.peek().doubleValue(), 1e-6);
        // implicit width = 48 + 8 + 4 + 30 + 16
        assertEquals(106.0, rl.implicitWidth.peek().doubleValue(), 1e-6);
        assertEquals(48.0, rl.implicitHeight.peek().doubleValue(), 1e-6);
        // cross-axis centred: a fills height, b centred in 48
        assertEquals(0.0, a.y.peek().doubleValue(), 1e-6);
        assertEquals(14.0, b.y.peek().doubleValue(), 1e-6);
    }

    @Test
    void rowLayoutFillWidthSharesExtra() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "import QtQuick.Layouts\n" +
            "Item {\n" +
            "  RowLayout {\n" +
            "    id: rl\n" +
            "    width: 200; spacing: 0\n" +
            "    Item { implicitWidth: 40; implicitHeight: 10 }\n" +
            "    Item { implicitWidth: 40; implicitHeight: 10; Layout.fillWidth: true }\n" +
            "  }\n" +
            "}");
        RowLayout rl = (RowLayout) root.children.get(0);
        rl.layout();
        Item b = rl.children.get(1);
        // fixed 40 + fill gets the remaining 160
        assertEquals(40.0, rl.children.get(0).width.peek().doubleValue(), 1e-6);
        assertEquals(160.0, b.width.peek().doubleValue(), 1e-6);
    }

    @Test
    void columnLayoutStacksVertically() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "import QtQuick.Layouts\n" +
            "Item {\n" +
            "  ColumnLayout {\n" +
            "    id: cl\n" +
            "    spacing: 6\n" +
            "    Item { implicitWidth: 30; implicitHeight: 20 }\n" +
            "    Item { implicitWidth: 50; implicitHeight: 40 }\n" +
            "  }\n" +
            "}");
        ColumnLayout cl = (ColumnLayout) root.children.get(0);
        cl.layout();
        Item a = cl.children.get(0);
        Item b = cl.children.get(1);
        assertEquals(0.0, a.y.peek().doubleValue(), 1e-6);
        assertEquals(26.0, b.y.peek().doubleValue(), 1e-6); // 20 + spacing 6
        assertEquals(66.0, cl.implicitHeight.peek().doubleValue(), 1e-6);
        assertEquals(50.0, cl.implicitWidth.peek().doubleValue(), 1e-6);
    }
}
