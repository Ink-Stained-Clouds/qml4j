package io.qml4j.render.items.view;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;
import io.qml4j.engine.Signal;

public class Loader extends Item {
    public final Property<String> source = new Property<>(null);
    public final Property<Component> sourceComponent = new Property<>(null);
    public final Property<Item> item = new Property<>(null);
    public final Property<Boolean> active = new Property<>(Boolean.TRUE);
    public final Signal loaded = new Signal();

    public String loadedSource;
    public Item loadedItem;
    public Component loadedComponent;
}
