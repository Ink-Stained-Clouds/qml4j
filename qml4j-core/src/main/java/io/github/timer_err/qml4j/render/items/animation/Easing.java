package io.github.timer_err.qml4j.render.items.animation;

import io.github.timer_err.qml4j.engine.binding.Property;

// QML easing value-type. `type` is a QEasingCurve::Type ordinal (Easing.* enum).
public final class Easing {
    public final Property<Number> type = new Property<>(0);
    // Accepted for OutBack/elastic etc.; the curve table doesn't use them yet.
    public final Property<Number> overshoot = new Property<>(1.70158);
    public final Property<Number> amplitude = new Property<>(1.0);
    public final Property<Number> period = new Property<>(0.3);
}
