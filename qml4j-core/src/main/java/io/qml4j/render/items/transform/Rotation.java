package io.qml4j.render.items.transform;

import io.qml4j.engine.binding.Property;

public class Rotation extends Transform {
    public final Property<Number> angle = new Property<>(0);
    public final Origin origin = new Origin();
    public final Axis axis = new Axis();
}
