package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Text extends Item {
    public final Property<String> text = new Property<>("");
    public final Property<String> color = new Property<>("#000000");
    public final Property<Number> fontSize = new Property<>(14);
}
