package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Column extends Item {
    public final Property<Number> spacing = new Property<>(0);

    public void layout() {
        double y = 0;
        double s = spacing.peek().doubleValue();
        double maxW = 0;
        for (Item c : children) {
            if (!c.visible.peek()) continue;
            c.y.set(y);
            double h = c.height.peek().doubleValue();
            y += h + s;
            double w = c.width.peek().doubleValue();
            if (w > maxW) maxW = w;
        }
        if (y > 0) y -= s;
        height.set(y);
        if (maxW > width.peek().doubleValue()) width.set(maxW);
    }
}
