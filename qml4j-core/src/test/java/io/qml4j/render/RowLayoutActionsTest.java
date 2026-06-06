package io.qml4j.render;

import io.qml4j.render.items.layout.RowLayout;
import io.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RowLayoutActionsTest {
    @Test
    void spacerPushesButtonsToRightWithinWidth() {
        RowLayout row = new RowLayout();
        row.width.set(315.0);
        row.spacing.set(8.0);

        Rectangle spacer = new Rectangle();
        spacer.Layout.fillWidth.set(Boolean.TRUE);
        row.children.add(spacer); spacer.parent.set(row);

        Rectangle cancel = new Rectangle();
        cancel.implicitWidth.set(69.0); cancel.width.set(69.0);
        row.children.add(cancel); cancel.parent.set(row);

        Rectangle del = new Rectangle();
        del.implicitWidth.set(93.0); del.width.set(93.0);
        row.children.add(del); del.parent.set(row);

        row.layout();
        row.layout(); // run twice: a fillWidth spacer must not ratchet wider

        double delRight = del.x.peek().doubleValue() + del.width.peek().doubleValue();
        System.out.println("spacer w=" + spacer.width.peek() + " cancel.x=" + cancel.x.peek()
            + " del.x=" + del.x.peek() + " delRight=" + delRight);
        assertTrue(delRight <= 315.0 + 1e-6, "last button stays within row width, right=" + delRight);
        assertEquals(315.0, delRight, 0.5, "last button flush to the right edge");
    }
}
