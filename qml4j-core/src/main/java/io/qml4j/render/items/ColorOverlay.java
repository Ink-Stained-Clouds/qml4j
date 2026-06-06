package io.qml4j.render.items;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

public class ColorOverlay extends QObject {
    public final Property<String> color = new Property<>("#000000");
}
