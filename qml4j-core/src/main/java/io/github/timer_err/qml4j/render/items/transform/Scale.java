package io.github.timer_err.qml4j.render.items.transform;

import io.github.timer_err.qml4j.engine.binding.Property;

public class Scale extends Transform {
    public final Property<Number> xScale = new Property<>(1);
    public final Property<Number> yScale = new Property<>(1);
    public final Origin origin = new Origin();
}
