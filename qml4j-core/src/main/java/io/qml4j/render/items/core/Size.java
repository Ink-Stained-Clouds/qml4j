package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;

// A width/height grouped value (e.g. Image.sourceSize).
public final class Size {
    public final Property<Number> width = new Property<>(-1);
    public final Property<Number> height = new Property<>(-1);
}
