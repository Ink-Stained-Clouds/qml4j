package io.qml4j.render.items.shape;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.QmlDefaultList;

import java.util.ArrayList;
import java.util.List;

@QmlDefaultList("elements")
public class Shape extends Item {
    public final List<ShapePath> elements = new ArrayList<>();
}
