package io.qml4j.render;

import io.qml4j.engine.Property;

public class Image extends Item {
    public final Property<String> source = new Property<>(null);

    io.github.humbleui.skija.Image skiaImage;
    String loadedSource;
    int intrinsicWidth;
    int intrinsicHeight;
}
