package io.github.timer_err.qml4j.render.items.handler;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;

// QtQuick TapHandler (a PointerHandler). v0 stub: properties + tap/long-press signals
// so a document loads and its handlers compile. Gesture recognition is not yet wired.
public class TapHandler extends Item {
    @SuppressWarnings("unused")
    public final Property<Number> gesturePolicy = new Property<>(0);
    @SuppressWarnings("unused")
    public final Property<Boolean> enabled = new Property<>(Boolean.TRUE);
    @SuppressWarnings("unused")
    public final Property<Boolean> pressed = new Property<>(Boolean.FALSE);
    @SuppressWarnings("unused")
    public final Property<Object> point = new Property<>(null);
    @SuppressWarnings("unused")
    public final Property<Number> longPressThreshold = new Property<>(0.4);
    @SuppressWarnings("unused")
    public final Signal tapped = new Signal();
    @SuppressWarnings("unused")
    public final Signal singleTapped = new Signal();
    @SuppressWarnings("unused")
    public final Signal doubleTapped = new Signal();
    @SuppressWarnings("unused")
    public final Signal longPressed = new Signal();

    public TapHandler() {
        visible.set(Boolean.FALSE);
    }
}
