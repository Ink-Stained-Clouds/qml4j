package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Layer {
    public final Property<Boolean> enabled = new Property<>(Boolean.FALSE);
    public final Property<Object> effect = new Property<>(null);
}
