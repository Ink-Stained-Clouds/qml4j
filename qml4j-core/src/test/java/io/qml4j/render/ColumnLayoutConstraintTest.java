package io.qml4j.render;

import io.qml4j.render.items.layout.ColumnLayout;
import io.qml4j.render.items.core.Item;
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
}
