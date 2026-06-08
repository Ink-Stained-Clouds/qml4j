package io.github.timer_err.qml4j.render.items.shape;

import io.github.timer_err.qml4j.engine.binding.Property;

public class PathQuad extends PathElement {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> controlX = new Property<>(0);
    public final Property<Number> controlY = new Property<>(0);
}
