package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.input.TextInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The MD3 TextField clear-button pattern: an inner TextInput bound `text: control.text`
// with `onTextChanged: control.text = text`, and an external write `control.text = ""`.
// Typing must not sever the inner binding, or the clear write never reaches the TextInput.
class TextFieldTwoWayClearTest {

    @Test
    void externalClearReachesInputAfterTyping() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Item { id: control\n" +
            "  property string text: \"\"\n" +
            "  TextInput { id: ti; text: control.text; onTextChanged: control.text = text }\n" +
            "}");
        TextInput ti = (TextInput) field(root, "ti");

        // Simulate the user typing (the event dispatcher path, not a QML imperative write).
        ti.setText("hello");
        ti.emitTextChanged();
        assertEquals("hello", ti.text.peek());
        assertEquals("hello", control(root));

        // The clear button: control.text = "" -- must propagate through the live binding.
        text(root).set("");
        assertEquals("", ti.text.peek(), "binding survived typing, so the clear reaches the input");
    }

    private static String control(Item root) {
        return (String) text(root).peek();
    }

    @SuppressWarnings("unchecked")
    private static io.qml4j.engine.binding.Property<String> text(Item root) {
        try {
            return (io.qml4j.engine.binding.Property<String>) root.getClass().getField("text").get(root);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Object field(Object root, String name) {
        try {
            return root.getClass().getField(name).get(root);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
