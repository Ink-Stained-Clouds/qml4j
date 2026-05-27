package io.qml4j.render;

import io.qml4j.render.items.Column;
import io.qml4j.render.items.Image;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Loader;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.Rectangle;
import io.qml4j.render.items.Text;

import io.qml4j.compiler.TypeRegistry;

public final class StockTypes {

    private StockTypes() {}

    public static TypeRegistry registry() {
        return new TypeRegistry()
            .register("Item", Item.class)
            .register("Rectangle", Rectangle.class)
            .register("Text", Text.class)
            .register("Column", Column.class)
            .register("MouseArea", MouseArea.class)
            .register("Image", Image.class)
            .register("Loader", Loader.class);
    }
}
