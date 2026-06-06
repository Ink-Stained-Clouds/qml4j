package io.qml4j.render.items.shape;

import io.qml4j.engine.QObject;
import io.qml4j.engine.QmlDefaultList;
import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

@QmlDefaultList("pathElements")
public class ShapePath extends QObject {
    public final List<PathElement> pathElements = new ArrayList<>();
    public final Property<Number> startX = new Property<>(0);
    public final Property<Number> startY = new Property<>(0);
    public final Property<String> strokeColor = new Property<>("#ffffff");
    public final Property<String> fillColor = new Property<>("#ffffff");
    public final Property<Number> strokeWidth = new Property<>(1);
    public final Property<String> capStyle = new Property<>("SquareCap");
    public final Property<String> joinStyle = new Property<>("BevelJoin");
    public final Property<String> fillRule = new Property<>("OddEvenFill");
}
