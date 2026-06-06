package io.qml4j.render;

import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

public final class Anchors {
    public final Property<Item> fill = new Property<>(null);
    public final Property<Item> centerIn = new Property<>(null);
    public final Property<Number> margins = new Property<>(0);
    public final Property<Number> leftMargin = new Property<>(Double.NaN);
    public final Property<Number> rightMargin = new Property<>(Double.NaN);
    public final Property<Number> topMargin = new Property<>(Double.NaN);
    public final Property<Number> bottomMargin = new Property<>(Double.NaN);

    public final Property<AnchorLine> left = new Property<>(null);
    public final Property<AnchorLine> right = new Property<>(null);
    public final Property<AnchorLine> top = new Property<>(null);
    public final Property<AnchorLine> bottom = new Property<>(null);
    public final Property<AnchorLine> horizontalCenter = new Property<>(null);
    public final Property<AnchorLine> verticalCenter = new Property<>(null);
    public final Property<Number> horizontalCenterOffset = new Property<>(0);
    public final Property<Number> verticalCenterOffset = new Property<>(0);
}
