package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.layout.ColumnLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The MD3 NavigationDrawer header is a bare Item (Layout.preferredHeight: 64, no
// fillWidth) holding a left-anchored title Text. With no natural width it must
// span the column (Qt stretches), not collapse to 0 width and centre to the
// middle -- which pushed the title to the drawer's right edge.
class ColumnLayoutZeroCrossFillTest {

    @Test
    void zeroWidthNonFillChildFillsColumn() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "import QtQuick.Layouts\n"
            + "Item {\n"
            + "  ColumnLayout {\n"
            + "    id: col\n"
            + "    width: 260\n"
            + "    Item { Layout.preferredHeight: 64 }\n"
            + "  }\n"
            + "}");
        ColumnLayout col = (ColumnLayout) root.children.get(0);
        col.width.set(260.0);
        col.layout();

        Item header = col.children.get(0);
        assertEquals(0.0, header.x.peek().doubleValue(), 1e-6, "header at left, not centred");
        assertEquals(260.0, header.width.peek().doubleValue(), 1e-6, "header fills column width");
    }

    // A sized child with no Layout.alignment is left-aligned (Qt default), keeping its
    // natural width -- e.g. an MD3 section label Text ("Top App Bar") sits at the left,
    // not centred. Upstream opts into centring with Qt.AlignHCenter.
    @Test
    void sizedNonFillChildLeftAligns() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "import QtQuick.Layouts\n"
            + "Item {\n"
            + "  ColumnLayout {\n"
            + "    id: col\n"
            + "    width: 100\n"
            + "    Item { implicitWidth: 40; implicitHeight: 20 }\n"
            + "  }\n"
            + "}");
        ColumnLayout col = (ColumnLayout) root.children.get(0);
        col.width.set(100.0);
        col.layout();

        Item child = col.children.get(0);
        assertEquals(0.0, child.x.peek().doubleValue(), 1e-6, "sized child left-aligned, not centred");
    }

    // Qt.AlignHCenter still centres (the opt-in path upstream uses for page titles).
    @Test
    void explicitHCenterStillCentres() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "import QtQuick.Layouts\n"
            + "Item {\n"
            + "  ColumnLayout {\n"
            + "    id: col\n"
            + "    width: 100\n"
            + "    Item { implicitWidth: 40; implicitHeight: 20; Layout.alignment: Qt.AlignHCenter }\n"
            + "  }\n"
            + "}");
        ColumnLayout col = (ColumnLayout) root.children.get(0);
        col.width.set(100.0);
        col.layout();

        Item child = col.children.get(0);
        assertEquals(30.0, child.x.peek().doubleValue(), 1e-6, "explicit AlignHCenter: (100-40)/2");
    }
}
