package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class Loader extends Item {
    public final Property<String> source = new Property<>(null);
    public final Property<Item> item = new Property<>(null);

    public String loadedSource;
    public Item loadedItem;
}
