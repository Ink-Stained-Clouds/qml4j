package io.github.timer_err.qml4j.render.items.layout;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;

public class Row extends Item {
    public final Property<Number> spacing = new Property<>(0);

    @Override
    public void layout() {
        double x = 0;
        double s = spacing.peekDouble();
        double maxH = 0;
        for (Item c : children) {
            if (!c.isVisible()) continue;
            c.x.set(x);
            double w = c.width.peekDouble();
            x += w + s;
            double h = c.height.peekDouble();
            if (h > maxH) maxH = h;
        }
        if (x > 0) x -= s;
        width.set(x);
        if (maxH > height.peekDouble()) height.set(maxH);
        // Publish the content size as implicit size (like RowLayout/Column do) so a
        // binding reading this Row's implicitWidth -- e.g. a SegmentedButton segment's
        // `Math.max(contentRow.implicitWidth + 24, 48)` -- sizes to the content instead
        // of reading 0 and collapsing.
        implicitWidth.set(x);
        implicitHeight.set(maxH);
    }
}
