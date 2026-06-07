package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;

public class Flickable extends Item {
    public final Property<Number> contentX = new Property<>(0);
    public final Property<Number> contentY = new Property<>(0);
    public final Property<Number> contentWidth = new Property<>(0);
    public final Property<Number> contentHeight = new Property<>(0);
    public final Property<String> flickableDirection = new Property<>("AutoFlickDirection");
    public final Property<Boolean> interactive = new Property<>(Boolean.TRUE);
    public final Property<Boolean> moving = new Property<>(Boolean.FALSE);
    public final Property<Number> boundsBehavior = new Property<>(3); // DragAndOvershootBounds
    // Content margins (Qt Flickable.topMargin/...): accepted so documents load; the
    // content is laid out by its own anchors/size here, so these are not yet applied.
    public final Property<Number> topMargin = new Property<>(0);
    public final Property<Number> bottomMargin = new Property<>(0);
    public final Property<Number> leftMargin = new Property<>(0);
    public final Property<Number> rightMargin = new Property<>(0);
}
