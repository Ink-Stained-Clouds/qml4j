package io.qml4j.render.items.window;

import io.qml4j.engine.binding.Property;

public class Button extends AbstractButton {
    public final Property<String> color = new Property<>("#3b6fe0");
    public final Property<String> textColor = new Property<>("#ffffff");
    public final Property<String> downColor = new Property<>("#2c54aa");
    public final Property<Number> radius = new Property<>(6);
    public final Property<Number> fontSize = new Property<>(16);
}
