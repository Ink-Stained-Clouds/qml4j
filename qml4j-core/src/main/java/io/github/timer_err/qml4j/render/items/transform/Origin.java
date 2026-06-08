package io.github.timer_err.qml4j.render.items.transform;

import io.github.timer_err.qml4j.engine.binding.Property;

// The pivot point a Rotation/Scale transforms around (grouped `origin.x`/`origin.y`).
public final class Origin {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
}
