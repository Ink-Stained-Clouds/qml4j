package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

// QML easing value-type. `type` is a QEasingCurve::Type ordinal (Easing.* enum).
public final class Easing {
    public final Property<Number> type = new Property<>(0);
}
