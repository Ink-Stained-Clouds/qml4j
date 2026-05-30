package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Image extends Item {
    public final Property<String> source = new Property<>(null);
    public final Property<String> fillMode = new Property<>("Stretch");
    public final Property<Number> paintedWidth = new Property<>(0);
    public final Property<Number> paintedHeight = new Property<>(0);

    public io.github.humbleui.skija.Image skiaImage;
    public String loadedSource;
    public int intrinsicWidth;
    public int intrinsicHeight;
}
