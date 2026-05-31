package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Control extends Item {
    public final Property<Boolean> enabled = new Property<>(Boolean.TRUE);
    public final Property<Number> padding = new Property<>(0);

    public final Property<Number> leftPadding = new Property<>(null);
    public final Property<Number> rightPadding = new Property<>(null);
    public final Property<Number> topPadding = new Property<>(null);
    public final Property<Number> bottomPadding = new Property<>(null);

    public float resolvedLeftPadding() { return pad(leftPadding); }
    public float resolvedRightPadding() { return pad(rightPadding); }
    public float resolvedTopPadding() { return pad(topPadding); }
    public float resolvedBottomPadding() { return pad(bottomPadding); }

    private float pad(Property<Number> side) {
        Number v = side.peek();
        if (v != null) return v.floatValue();
        Number p = padding.peek();
        return p == null ? 0f : p.floatValue();
    }
}
