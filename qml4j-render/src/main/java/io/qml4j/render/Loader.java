package io.qml4j.render;

import io.qml4j.engine.Property;

public class Loader extends Item {
    public final Property<String> source = new Property<>(null);
    public final Property<Item> item = new Property<>(null);

    String loadedSource;
    Item loadedItem;
}
