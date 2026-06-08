package io.github.timer_err.qml4j.render.items.handler;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;

// QtQuick DragHandler (a PointerHandler). v0 stub: properties + active/translation
// signals so a document loads. Drag tracking is not yet wired.
public class DragHandler extends Item {
    public final Property<Object> target = new Property<>(null);
    public final Property<Boolean> active = new Property<>(Boolean.FALSE);
    public final Property<Boolean> enabled = new Property<>(Boolean.TRUE);
    public final Property<Number> grabPermissions = new Property<>(0);
    public final Property<Object> centroid = new Property<>(null);
    public final Signal translationChanged = new Signal();
    public final Signal activeChanged = new Signal();

    public DragHandler() {
        visible.set(Boolean.FALSE);
    }
}
