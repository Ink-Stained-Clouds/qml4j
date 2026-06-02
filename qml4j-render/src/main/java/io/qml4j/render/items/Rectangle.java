package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Rectangle extends Item {
    public final Property<String> color = new Property<>("#ffffff");
    public final Property<Number> radius = new Property<>(0);
    // Per-corner radius; -1 = unset (falls back to `radius`).
    public final Property<Number> topLeftRadius = new Property<>(-1);
    public final Property<Number> topRightRadius = new Property<>(-1);
    public final Property<Number> bottomLeftRadius = new Property<>(-1);
    public final Property<Number> bottomRightRadius = new Property<>(-1);
    public final Border border = new Border();

    public float cornerRadius(float corner) {
        return corner >= 0 ? corner : radius.peek().floatValue();
    }
    public final Property<Gradient> gradient = new Property<>(null);
}
