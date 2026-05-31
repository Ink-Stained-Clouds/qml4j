package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class TextField extends TextInput {
    public final Property<String> placeholderText = new Property<>("");
    public final Property<String> placeholderColor = new Property<>("#9098a4");
    public final Property<String> backgroundColor = new Property<>("#ffffff");
    public final Property<String> borderColor = new Property<>("#808890");
    public final Property<String> focusBorderColor = new Property<>("#3b6fe0");
    public final Property<Number> borderWidth = new Property<>(1);
    public final Property<Number> radius = new Property<>(6);
    public final Property<Number> padding = new Property<>(8);
}
