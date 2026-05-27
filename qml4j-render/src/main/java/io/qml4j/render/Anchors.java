package io.qml4j.render;

import io.qml4j.engine.Property;

public final class Anchors {
    public final Property<Item> fill = new Property<>(null);
    public final Property<Item> centerIn = new Property<>(null);
    public final Property<Number> margins = new Property<>(0);
    public final Property<Number> leftMargin = new Property<>(Double.NaN);
    public final Property<Number> rightMargin = new Property<>(Double.NaN);
    public final Property<Number> topMargin = new Property<>(Double.NaN);
    public final Property<Number> bottomMargin = new Property<>(Double.NaN);
}
