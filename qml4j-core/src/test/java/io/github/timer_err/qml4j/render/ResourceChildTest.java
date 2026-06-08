package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ResourceChildTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    // A Behavior is a non-visual property modifier: it must not occupy a slot in `children`,
    // so `children[0]` resolves to the first real visual child (Qt parity).
    @Test
    void behaviorIsNotAVisualChild() {
        Item root = newView().load(
            "import QtQuick\n" +
            "Item {\n" +
            "  property real s: 1\n" +
            "  Behavior on s { NumberAnimation { duration: 100 } }\n" +
            "  Rectangle { id: r }\n" +
            "}");
        assertEquals(1, root.children.size());
        assertInstanceOf(Rectangle.class, root.children.get(0));
    }

    // A wrapper sizing to its first child (`implicitHeight: container.children[0].implicitHeight`)
    // must pick up content parented AFTER the wrapper was constructed (default-property content
    // at the use site), and a Behavior in the container must not be read as children[0].
    // This is exactly the MD3 DraggableWidgetWrapper case.
    @Test
    void wrapperSizesToContentAddedAtUseSite() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("probe/qmldir", "Wrap 1.0 Wrap.qml".getBytes(StandardCharsets.UTF_8));
        files.put("probe/Wrap.qml",
            ("import QtQuick\n" +
             "Item { id: wrapper\n" +
             "  default property alias content: container.data\n" +
             "  implicitHeight: container.children.length > 0 ? container.children[0].implicitHeight : -1\n" +
             "  Item { id: container; anchors.fill: parent\n" +
             "    Behavior on scale { NumberAnimation { duration: 100 } }\n" +
             "  }\n" +
             "}\n").getBytes(StandardCharsets.UTF_8));

        QmlView v = newView();
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import probe\n" +
            "Item { Wrap { id: w; Rectangle { implicitHeight: 88 } } }");

        Item w = (Item) reflectField(root, "w");
        Property<?> implH = w.implicitHeight;
        assertEquals(88.0, ((Number) implH.peek()).doubleValue(), 1e-6,
            "wrapper picks up content's implicitHeight, not the Behavior's");
    }

    private static Object reflectField(Object root, String name) {
        try {
            return root.getClass().getField(name).get(root);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
