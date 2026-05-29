package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Rectangle extends Item {
    public final Property<String> color = new Property<>("#ffffff");
    public final Property<Number> radius = new Property<>(0);
    public final Border border = new Border();
    public final Property<Gradient> gradient = new Property<>(null);
}
