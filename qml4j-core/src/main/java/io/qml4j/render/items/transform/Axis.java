package io.qml4j.render.items.transform;

import io.qml4j.engine.binding.Property;

// Rotation axis (grouped `axis.x`/`axis.y`/`axis.z`). Default is the z axis -- a plain
// 2D in-plane rotation; an x/y axis is a 3D flip the 2D renderer approximates.
public final class Axis {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> z = new Property<>(1);
}
