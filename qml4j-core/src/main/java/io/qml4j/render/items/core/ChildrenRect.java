package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;

// QML Item.childrenRect: the bounding rectangle of an item's children, in the
// item's own coordinates. Computed by the renderer's measure pass.
public final class ChildrenRect {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> width = new Property<>(0);
    public final Property<Number> height = new Property<>(0);
}
