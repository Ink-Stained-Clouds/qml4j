package io.qml4j.render.items;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

public class GradientStop extends QObject {
    public final Property<Number> position = new Property<>(0);
    public final Property<String> color = new Property<>("#000000");
}
