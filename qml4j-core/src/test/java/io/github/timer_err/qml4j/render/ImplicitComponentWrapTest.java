package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.view.Component;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Qt implicit Component wrapping: an inline object assigned to a `property Component`
// becomes the template of a Component, not a live child. Carousel's `delegate: Card {}`
// relies on this -- the Carousel then reads root.delegate as a Component for its Loader.
class ImplicitComponentWrapTest {

    @Test
    void inlineObjectAssignedToComponentPropertyIsWrapped() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put("probe/qmldir", "Holder 1.0 Holder.qml".getBytes(StandardCharsets.UTF_8));
        files.put("probe/Holder.qml",
            "import QtQuick\nItem { property Component slot }\n".getBytes(StandardCharsets.UTF_8));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import probe\n" +
            "Item {\n" +
            "  Holder { id: h; slot: Rectangle { width: 5 } }\n" +
            "}");

        Item holder = (Item) root.getClass().getField("h").get(root);
        Property<?> slot = (Property<?>) holder.getClass().getField("slot").get(holder);
        Object wrapped = slot.peek();
        assertNotNull(wrapped, "Component property assigned an inline object must be set");
        assertInstanceOf(Component.class, wrapped,
            "inline object assigned to a Component property is wrapped in a Component");
    }
}
