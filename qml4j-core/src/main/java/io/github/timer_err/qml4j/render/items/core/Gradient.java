package io.github.timer_err.qml4j.render.items.core;

import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.QmlDefaultList;
import io.github.timer_err.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

@QmlDefaultList("stops")
public class Gradient extends QObject {
    public final List<GradientStop> stops = new ArrayList<>();
    // Gradient.Vertical (0, default) or Gradient.Horizontal (1).
    public final Property<Number> orientation = new Property<>(0);
}
