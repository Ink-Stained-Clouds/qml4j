package io.github.timer_err.qml4j.render.items.effect;

import io.github.timer_err.qml4j.engine.binding.Property;

public class Layer {
    public final Property<Boolean> enabled = new Property<>(Boolean.FALSE);
    public final Property<Object> effect = new Property<>(null);
    @SuppressWarnings("unused")
    public final Property<Boolean> smooth = new Property<>(Boolean.FALSE);
}
