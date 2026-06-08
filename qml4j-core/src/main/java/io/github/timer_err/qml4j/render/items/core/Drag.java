package io.github.timer_err.qml4j.render.items.core;

import io.github.timer_err.qml4j.engine.binding.Property;

public final class Drag {
    public final Property<Item> target = new Property<>(null);
    public final Property<String> axis = new Property<>("XAndYAxis");
    public final Property<Number> minimumX = new Property<>(Double.NEGATIVE_INFINITY);
    public final Property<Number> maximumX = new Property<>(Double.POSITIVE_INFINITY);
    public final Property<Number> minimumY = new Property<>(Double.NEGATIVE_INFINITY);
    public final Property<Number> maximumY = new Property<>(Double.POSITIVE_INFINITY);
    public final Property<Boolean> active = new Property<>(Boolean.FALSE);
}
