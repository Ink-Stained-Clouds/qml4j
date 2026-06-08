package io.qml4j.render;

import io.qml4j.render.items.layout.ColumnLayout;
import io.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnLayoutConstraintTest {
    @Test
    void constrainedWidthDrivesFillAndAlignNotContent() {
        ColumnLayout col = new ColumnLayout();
        col.width.set(200.0);

        // A fillWidth child wider (natural) than the column: must clamp to 200,
        // not balloon the box to its 500 natural width.
        Rectangle wide = new Rectangle();
        wide.implicitWidth.set(500.0);
        wide.Layout.fillWidth.set(Boolean.TRUE);
        col.children.add(wide); wide.parent.set(col);

        // A narrow, horizontally-centred child: centred within 200, not within 500.
        Rectangle dot = new Rectangle();
        dot.implicitWidth.set(40.0);
        dot.width.set(40.0);
        dot.Layout.alignment.set(4); // Qt.AlignHCenter
        col.children.add(dot); dot.parent.set(col);

        col.layout();

        assertEquals(200.0, wide.width.peek().doubleValue(), 1e-6, "fillWidth clamps to column width");
        assertEquals(80.0, dot.x.peek().doubleValue(), 1e-6, "HCenter centres within column width");
    }

    // A fillWidth child with Layout.maximumWidth caps at the max (a square widget in a wide
    // column stays square) and centres in the remaining space.
    @Test
    void fillWidthHonoursMaximumWidth() {
        ColumnLayout col = new ColumnLayout();
        col.width.set(600.0);

        Rectangle box = new Rectangle();
        box.implicitWidth.set(300.0);
        box.Layout.fillWidth.set(Boolean.TRUE);
        box.Layout.maximumWidth.set(320.0);
        box.Layout.alignment.set(4); // Qt.AlignHCenter
        col.children.add(box); box.parent.set(col);

        col.layout();

        assertEquals(320.0, box.width.peek().doubleValue(), 1e-6, "fillWidth capped at maximumWidth");
        assertEquals(140.0, box.x.peek().doubleValue(), 1e-6, "capped fill centres in the column");
    }

    // fillWidth + a horizontal Layout.alignment and NO maximumWidth: the child shrinks to
    // its content and aligns, instead of stretching edge-to-edge (a centred RowLayout of
    // buttons centres as a group rather than left-packing). Qt parity.
    @Test
    void fillWidthWithAlignmentShrinksToContent() {
        ColumnLayout col = new ColumnLayout();
        col.width.set(600.0);

        Rectangle group = new Rectangle();
        group.implicitWidth.set(450.0);
        group.Layout.fillWidth.set(Boolean.TRUE);
        group.Layout.alignment.set(4); // Qt.AlignHCenter, no maximumWidth
        col.children.add(group); group.parent.set(col);

        col.layout();

        assertEquals(450.0, group.width.peek().doubleValue(), 1e-6, "shrinks to content, not full column");
        assertEquals(75.0, group.x.peek().doubleValue(), 1e-6, "centres the shrunk content");
    }

    // A fillWidth + AlignHCenter child that itself contains a fillWidth child (a
    // NavigationRail's item column whose rows fillWidth) must FILL, not shrink-to-content --
    // otherwise the rows/pills collapse to the icon width.
    @Test
    void fillWidthWithAlignmentStillFillsWhenItHasAFillWidthChild() {
        ColumnLayout col = new ColumnLayout();
        col.width.set(600.0);

        ColumnLayout inner = new ColumnLayout();
        inner.Layout.fillWidth.set(Boolean.TRUE);
        inner.Layout.alignment.set(4); // Qt.AlignHCenter, no maximumWidth
        col.children.add(inner); inner.parent.set(col);

        Rectangle row = new Rectangle();
        row.implicitWidth.set(120.0);
        row.Layout.fillWidth.set(Boolean.TRUE);   // a row that wants the full width
        inner.children.add(row); row.parent.set(inner);

        col.layout();

        assertEquals(600.0, inner.width.peek().doubleValue(), 1e-6, "fills since a child wants the width");
        assertEquals(0.0, inner.x.peek().doubleValue(), 1e-6);
    }
}
