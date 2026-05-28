package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Row extends Item {
    public final Property<Number> spacing = new Property<>(0);

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
    }
}
