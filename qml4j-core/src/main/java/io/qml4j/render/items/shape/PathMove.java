package io.qml4j.render.items.shape;

import io.qml4j.engine.binding.Property;

public class PathMove extends PathElement {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
}
