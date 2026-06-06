package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Flickable extends Item {
    public final Property<Number> contentX = new Property<>(0);
    public final Property<Number> contentY = new Property<>(0);
    public final Property<Number> contentWidth = new Property<>(0);
    public final Property<Number> contentHeight = new Property<>(0);
    public final Property<String> flickableDirection = new Property<>("AutoFlickDirection");
    public final Property<Boolean> interactive = new Property<>(Boolean.TRUE);
    public final Property<Boolean> moving = new Property<>(Boolean.FALSE);
}
