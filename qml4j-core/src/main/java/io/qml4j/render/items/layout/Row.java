package io.qml4j.render.items.layout;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

public class Row extends Item {
    public final Property<Number> spacing = new Property<>(0);

    @Override
    public void layout() {
        double x = 0;
        double s = spacing.peek().doubleValue();
        double maxH = 0;
        for (Item c : children) {
            if (!c.visible.peek()) continue;
            c.x.set(x);
            double w = c.width.peek().doubleValue();
            x += w + s;
            double h = c.height.peek().doubleValue();
            if (h > maxH) maxH = h;
        }
        if (x > 0) x -= s;
        width.set(x);
        if (maxH > height.peek().doubleValue()) height.set(maxH);
        // Publish the content size as implicit size (like RowLayout/Column do) so a
        // binding reading this Row's implicitWidth -- e.g. a SegmentedButton segment's
        // `Math.max(contentRow.implicitWidth + 24, 48)` -- sizes to the content instead
        // of reading 0 and collapsing.
        implicitWidth.set(x);
        implicitHeight.set(maxH);
    }
}
