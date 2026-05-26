package io.qml4j.render;

import io.qml4j.engine.Property;
import io.qml4j.engine.QObject;

import java.util.ArrayList;
import java.util.List;

public class Item extends QObject {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> width = new Property<>(0);
    public final Property<Number> height = new Property<>(0);
    public final Property<Boolean> visible = new Property<>(Boolean.TRUE);
    public final Property<Number> opacity = new Property<>(1.0);
    public final Property<Item> parent = new Property<>(null);
    public final List<Item> children = new ArrayList<>();
}
