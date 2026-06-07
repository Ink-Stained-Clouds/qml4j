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

    @Override
    public void layout() {
        if (flow.peekInt() == 1) layoutTopToBottom();
        else layoutLeftToRight();
    }

    private void layoutLeftToRight() {
        double s = spacing.peekDouble();
        double bound = width.peekDouble();
        double x = 0, y = 0, rowH = 0, maxRowW = 0;
        for (Item c : children) {
            if (!c.isVisible()) continue;
            double cw = c.width.peekDouble();
            double ch = c.height.peekDouble();
            if (x > 0 && bound > 0 && x + cw > bound) {
                maxRowW = Math.max(maxRowW, x - s);
                x = 0; y += rowH + s; rowH = 0;
            }
            c.x.set(x);
            c.y.set(y);
            x += cw + s;
            if (ch > rowH) rowH = ch;
        }
        maxRowW = Math.max(maxRowW, x > 0 ? x - s : 0);
        implicitWidth.set(maxRowW);
        double total = y + rowH;
        implicitHeight.set(total);
        if (total > height.peekDouble()) height.set(total);
    }

    private void layoutTopToBottom() {
        double s = spacing.peekDouble();
        double bound = height.peekDouble();
        double x = 0, y = 0, colW = 0, maxColH = 0;
        for (Item c : children) {
            if (!c.isVisible()) continue;
            double cw = c.width.peekDouble();
            double ch = c.height.peekDouble();
            if (y > 0 && bound > 0 && y + ch > bound) {
                maxColH = Math.max(maxColH, y - s);
                y = 0; x += colW + s; colW = 0;
            }
            c.x.set(x);
            c.y.set(y);
            y += ch + s;
            if (cw > colW) colW = cw;
        }
        maxColH = Math.max(maxColH, y > 0 ? y - s : 0);
        implicitHeight.set(maxColH);
        double total = x + colW;
        implicitWidth.set(total);
        if (total > width.peekDouble()) width.set(total);
    }
}
