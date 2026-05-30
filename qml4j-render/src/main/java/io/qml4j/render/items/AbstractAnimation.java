package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public abstract class AbstractAnimation extends Item implements Animatable {
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);

    protected AbstractAnimation() {
        visible.set(Boolean.FALSE);
    }
}
