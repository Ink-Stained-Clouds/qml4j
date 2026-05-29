package io.qml4j.render;

import io.qml4j.render.items.Behavior;
import io.qml4j.render.items.Column;
import io.qml4j.render.items.Component;
import io.qml4j.render.items.Connections;
import io.qml4j.render.items.Flickable;
import io.qml4j.render.items.Gradient;
import io.qml4j.render.items.GradientStop;
import io.qml4j.render.items.Image;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.ListElement;
import io.qml4j.render.items.ListModel;
import io.qml4j.render.items.Loader;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.NumberAnimation;
import io.qml4j.render.items.PropertyChanges;
import io.qml4j.render.items.Rectangle;
import io.qml4j.render.items.Repeater;
import io.qml4j.render.items.Row;
import io.qml4j.render.items.State;
import io.qml4j.render.items.Text;
import io.qml4j.render.items.TextInput;
import io.qml4j.render.items.Timer;
import io.qml4j.render.items.Transition;

import io.qml4j.compiler.TypeRegistry;

public final class StockTypes {

    private StockTypes() {}

    public static TypeRegistry registry() {
        return new TypeRegistry()
            .register("Item", Item.class)
            .register("Rectangle", Rectangle.class)
            .register("Text", Text.class)
            .register("Column", Column.class)
            .register("Row", Row.class)
            .register("Timer", Timer.class)
            .register("MouseArea", MouseArea.class)
            .register("Image", Image.class)
            .register("Loader", Loader.class)
            .register("NumberAnimation", NumberAnimation.class)
            .register("State", State.class)
            .register("PropertyChanges", PropertyChanges.class)
            .register("Transition", Transition.class)
            .register("Behavior", Behavior.class)
            .register("Repeater", Repeater.class)
            .register("ListModel", ListModel.class)
            .register("ListElement", ListElement.class)
            .register("Component", Component.class)
            .register("Connections", Connections.class)
            .register("Flickable", Flickable.class)
            .register("Gradient", Gradient.class)
            .register("GradientStop", GradientStop.class)
            .register("TextInput", TextInput.class);
    }
}
