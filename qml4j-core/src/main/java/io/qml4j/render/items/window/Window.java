package io.qml4j.render.items.window;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

public class Window extends Item {
    public final Property<String> color = new Property<>("#ffffff");
    public final Property<String> title = new Property<>("");
}
