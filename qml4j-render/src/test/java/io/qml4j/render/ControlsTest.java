package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.Button;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Label;
import io.qml4j.render.items.TextField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlsTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void buttonTextAndClicked() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  width: 200; height: 100\n" +
            "  Button {\n" +
            "    id: b; x: 10; y: 10; width: 120; height: 40\n" +
            "    text: \"Tap me\"\n" +
            "    property int taps: 0\n" +
            "    onClicked: taps = taps + 1\n" +
            "  }\n" +
            "}");
        Button b = (Button) root.children.get(0);
        assertEquals("Tap me", b.text.peek());
        v.dispatchPointerDown(40, 30);
        v.dispatchPointerUp(40, 30);
        assertEquals(1L, readProp(b, "taps"));
    }

    @Test
    void buttonPressedState() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  Button { x: 0; y: 0; width: 100; height: 40; text: \"x\" }\n" +
            "}");
        Button b = (Button) root.children.get(0);
        v.dispatchPointerDown(20, 20);
        assertTrue(b.isPressed.peek());
        v.dispatchPointerUp(20, 20);
        assertEquals(Boolean.FALSE, b.isPressed.peek());
    }

    @Test
    void buttonDefaults() {
        Item root = newView().load("Button { text: \"k\" }");
        Button b = (Button) root;
        assertEquals("#3b6fe0", b.color.peek());
        assertEquals("#ffffff", b.textColor.peek());
        assertEquals(Boolean.TRUE, b.enabled.peek());
    }

    @Test
    void labelIsText() {
        Item root = newView().load("Label { text: \"hi\"; color: \"#123456\"; fontSize: 20 }");
        assertInstanceOf(io.qml4j.render.items.Text.class, root);
        Label l = (Label) root;
        assertEquals("hi", l.text.peek());
        assertEquals("#123456", l.color.peek());
        assertEquals(20L, l.fontSize.peek().longValue());
    }

    @Test
    void textFieldEditsAndPlaceholder() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 300; height: 80\n" +
            "  TextField {\n" +
            "    id: tf; x: 0; y: 0; width: 200; height: 40\n" +
            "    placeholderText: \"name\"\n" +
            "    focus: true\n" +
            "  }\n" +
            "}");
        TextField tf = (TextField) root.children.get(0);
        assertEquals("name", tf.placeholderText.peek());
        assertEquals("", tf.text.peek());
        tf.cursorPosition.set(0);
        v.dispatchKey(72, "h", true);
        v.dispatchKey(105, "i", true);
        assertEquals("hi", tf.text.peek());
    }

    @Test
    void textFieldIsTextInput() {
        Item root = newView().load("TextField { width: 120; height: 36 }");
        assertInstanceOf(io.qml4j.render.items.TextInput.class, root);
        assertEquals("#ffffff", ((TextField) root).backgroundColor.peek());
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
