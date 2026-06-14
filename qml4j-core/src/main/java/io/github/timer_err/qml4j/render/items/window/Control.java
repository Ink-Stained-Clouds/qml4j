package io.github.timer_err.qml4j.render.items.window;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;

public class Control extends Item {
    public final Property<Number> padding = new Property<>(0);

    public final Property<Number> leftPadding = new Property<>(null);
    public final Property<Number> rightPadding = new Property<>(null);
    public final Property<Number> topPadding = new Property<>(null);
    public final Property<Number> bottomPadding = new Property<>(null);

    @SuppressWarnings("unused")
    public float resolvedLeftPadding() { return pad(leftPadding); }
    @SuppressWarnings("unused")
    public float resolvedRightPadding() { return pad(rightPadding); }
    @SuppressWarnings("unused")
    public float resolvedTopPadding() { return pad(topPadding); }
    @SuppressWarnings("unused")
    public float resolvedBottomPadding() { return pad(bottomPadding); }

    private float pad(Property<Number> side) {
        Number v = side.peek();
        if (v != null) return v.floatValue();
        Number p = padding.peek();
        return p == null ? 0f : p.floatValue();
    }
}
