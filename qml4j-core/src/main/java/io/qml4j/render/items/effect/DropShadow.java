package io.qml4j.render.items.effect;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

public class DropShadow extends QObject {
    public final Property<Number> offsetX = new Property<>(0);
    public final Property<Number> offsetY = new Property<>(0);
    public final Property<Number> radius = new Property<>(0);
    public final Property<String> color = new Property<>("#000000");
}
