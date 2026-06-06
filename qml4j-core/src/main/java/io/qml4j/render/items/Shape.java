package io.qml4j.render.items;

import io.qml4j.engine.QmlDefaultList;

import java.util.ArrayList;
import java.util.List;

@QmlDefaultList("elements")
public class Shape extends Item {
    public final List<ShapePath> elements = new ArrayList<>();
}
