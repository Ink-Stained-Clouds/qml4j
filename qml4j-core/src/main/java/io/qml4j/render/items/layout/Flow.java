package io.qml4j.render.items.layout;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

// QtQuick Flow positioner: lays children out like Row (LeftToRight) or Column
// (TopToBottom) but wraps to a new line once the next child would cross the
// Flow's bound -- its width for LeftToRight, its height for TopToBottom. Children
// keep their own sizes; Flow only positions them. Unlike Row/Column it never
// overrides the bound axis (the wrap boundary is the externally given size); it
// only grows along the perpendicular axis to fit the wrapped content.
public class Flow extends Item {
    public final Property<Number> spacing = new Property<>(0);
    public final Property<Number> flow = new Property<>(0); // 0 LeftToRight, 1 TopToBottom
    public final Property<Number> padding = new Property<>(0);

    @Override
    public void layout() {
        if (flow.peekInt() == 1) layoutTopToBottom();
        else layoutLeftToRight();
    }

    // A self-derived size (the item follows its own implicitWidth/Height, with no external
    // width/height anchor or Layout) gives no constraint to wrap against -- a legend Flow
    // whose container sizes to it would otherwise self-reinforce down to one item per row.
    private boolean ownsWidth() {
        if (Double.isNaN(lastImplicitWidth)) return width.peekDouble() == 0.0;
        return width.peekDouble() == lastImplicitWidth;
    }

    private boolean ownsHeight() {
        if (Double.isNaN(lastImplicitHeight)) return height.peekDouble() == 0.0;
        return height.peekDouble() == lastImplicitHeight;
    }

    private void layoutLeftToRight() {
        double s = spacing.peekDouble();
        double p = padding.peekDouble();
        double bound = ownsWidth() ? 0 : width.peekDouble() - 2 * p;
        double x = 0, y = 0, rowH = 0, maxRowW = 0;
        for (Item c : children) {
            if (!c.isVisible()) continue;
            double cw = c.width.peekDouble();
            double ch = c.height.peekDouble();
            if (x > 0 && bound > 0 && x + cw > bound) {
                maxRowW = Math.max(maxRowW, x - s);
                x = 0; y += rowH + s; rowH = 0;
            }
            c.x.set(x + p);
            c.y.set(y + p);
            x += cw + s;
            if (ch > rowH) rowH = ch;
        }
        maxRowW = Math.max(maxRowW, x > 0 ? x - s : 0);
        implicitWidth.set(maxRowW + 2 * p);
        // Only publish implicitHeight; the Renderer's followImplicitSize tracks it onto
        // height (and shrinks it). Setting height here directly was grow-only, so a wide
        // window that re-wrapped to fewer rows kept the taller initial height -- leaving
        // empty space the Flickable could still scroll into past the last row.
        implicitHeight.set(y + rowH + 2 * p);
    }

    private void layoutTopToBottom() {
        double s = spacing.peekDouble();
        double p = padding.peekDouble();
        double bound = ownsHeight() ? 0 : height.peekDouble() - 2 * p;
        double x = 0, y = 0, colW = 0, maxColH = 0;
        for (Item c : children) {
            if (!c.isVisible()) continue;
            double cw = c.width.peekDouble();
            double ch = c.height.peekDouble();
            if (y > 0 && bound > 0 && y + ch > bound) {
                maxColH = Math.max(maxColH, y - s);
                y = 0; x += colW + s; colW = 0;
            }
            c.x.set(x + p);
            c.y.set(y + p);
            y += ch + s;
            if (cw > colW) colW = cw;
        }
        maxColH = Math.max(maxColH, y > 0 ? y - s : 0);
        implicitHeight.set(maxColH + 2 * p);
        implicitWidth.set(x + colW + 2 * p);
    }
}
