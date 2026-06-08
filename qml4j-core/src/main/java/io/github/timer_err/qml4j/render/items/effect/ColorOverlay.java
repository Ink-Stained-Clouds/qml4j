package io.github.timer_err.qml4j.render.items.effect;

import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.binding.Property;

public class ColorOverlay extends QObject {
    public final Property<String> color = new Property<>("#000000");
}
