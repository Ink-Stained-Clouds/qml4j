package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.layout.RowLayout;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
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

    // A fillWidth child shrinks below its implicit width when the row is constrained
    // narrower than its content, so a wrapping label wraps instead of overflowing.
    @Test
    void fillWidthChildShrinksWhenRowNarrowerThanContent() {
        RowLayout row = new RowLayout();
        row.width.set(100.0);

        Rectangle fixed = new Rectangle();
        fixed.implicitWidth.set(40.0); fixed.width.set(40.0);
        row.children.add(fixed); fixed.parent.set(row);

        Rectangle fill = new Rectangle();
        fill.Layout.fillWidth.set(Boolean.TRUE);
        fill.implicitWidth.set(200.0);
        row.children.add(fill); fill.parent.set(row);

        row.layout();

        assertEquals(40.0, fixed.width.peek().doubleValue(), 1e-6);
        assertEquals(60.0, fill.width.peek().doubleValue(), 1e-6, "fill shrinks to available width");
    }
}
