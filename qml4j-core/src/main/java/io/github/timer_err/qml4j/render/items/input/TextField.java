package io.github.timer_err.qml4j.render.items.input;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.Painter;

public class TextField extends TextInput {
    public final Property<String> placeholderText = new Property<>("");
    public final Property<String> placeholderTextColor = new Property<>("#9098a4");
    public final Property<String> backgroundColor = new Property<>("#ffffff");
    public final Property<String> borderColor = new Property<>("#808890");
    public final Property<String> focusBorderColor = new Property<>("#3b6fe0");
    public final Property<Number> borderWidth = new Property<>(1);
    public final Property<Number> radius = new Property<>(6);
    public final Property<Number> padding = new Property<>(8);

    public TextField() {
        // The base TextInput fields are wired by its constructor; add the field chrome here.
        wireContentInvalidation(placeholderText, placeholderTextColor, backgroundColor,
            borderColor, focusBorderColor, borderWidth, radius, padding, focus);
    }

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        p.drawTextField(this, w, h, alpha);
    }
}
