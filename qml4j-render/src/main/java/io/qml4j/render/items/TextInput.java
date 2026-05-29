package io.qml4j.render.items;

import io.qml4j.engine.Signal;
import io.qml4j.engine.binding.Property;

public class TextInput extends Item {
    public final Property<String> text = new Property<>("");
    public final Property<String> color = new Property<>("#000000");
    public final Property<String> cursorColor = new Property<>("#000000");
    public final Property<Number> fontSize = new Property<>(16);
    public final Property<Number> cursorPosition = new Property<>(0);
    public final Property<Number> maximumLength = new Property<>(Integer.MAX_VALUE);
    public final Property<Boolean> readOnly = new Property<>(Boolean.FALSE);

    public final Signal textChanged = new Signal();
    public final Signal accepted = new Signal();
}
