package io.github.timer_err.qml4j.render.items.shape;

import io.github.timer_err.qml4j.engine.binding.Property;

public class PathCubic extends PathElement {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> control1X = new Property<>(0);
    public final Property<Number> control1Y = new Property<>(0);
    public final Property<Number> control2X = new Property<>(0);
    public final Property<Number> control2Y = new Property<>(0);
}
