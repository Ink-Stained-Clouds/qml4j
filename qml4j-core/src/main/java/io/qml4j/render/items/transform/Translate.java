package io.qml4j.render.items.transform;

import io.qml4j.engine.binding.Property;

public class Translate extends Transform {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
}
