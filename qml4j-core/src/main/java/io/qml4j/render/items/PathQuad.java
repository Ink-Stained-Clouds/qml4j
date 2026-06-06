package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class PathQuad extends PathElement {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> controlX = new Property<>(0);
    public final Property<Number> controlY = new Property<>(0);
}
