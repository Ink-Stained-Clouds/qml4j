package io.github.timer_err.qml4j.render.items.shape;

import io.github.timer_err.qml4j.engine.binding.Property;

public class PathLine extends PathElement {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
}
