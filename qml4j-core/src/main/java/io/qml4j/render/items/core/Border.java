package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;

public final class Border {
    public final Property<Number> width = new Property<>(0);
    public final Property<String> color = new Property<>("#000000");
}
