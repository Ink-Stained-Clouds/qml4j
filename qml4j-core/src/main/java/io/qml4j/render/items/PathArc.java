package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class PathArc extends PathElement {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> radiusX = new Property<>(0);
    public final Property<Number> radiusY = new Property<>(0);
    public final Property<Number> xAxisRotation = new Property<>(0);
    public final Property<Boolean> useLargeArc = new Property<>(Boolean.FALSE);
    public final Property<String> direction = new Property<>("Clockwise");
}
