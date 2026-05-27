package io.qml4j.render;

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
