package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeysTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void pressedHandlerReceivesEvent() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  focus: true\n" +
            "  property int lastKey: 0\n" +
            "  Keys.onPressed: { lastKey = event.key; event.accepted = true }\n" +
            "}");
        boolean handled = v.dispatchKey(65, "a", true);
        assertTrue(handled);
        assertEquals(65, ((Number) readProp(root, "lastKey")).intValue());
    }

    @Test
    void unacceptedKeyReturnsFalse() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  focus: true\n" +
            "  property int hits: 0\n" +
            "  Keys.onPressed: hits = hits + 1\n" +
            "}");
        boolean handled = v.dispatchKey(65, "a", true);
        assertFalse(handled);
        assertEquals(1L, readProp(root, "hits"));
    }

    @Test
    void acceptingStopsBubbling() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  property int outerHits: 0\n" +
            "  Keys.onPressed: outerHits = outerHits + 1\n" +
            "  Item {\n" +
            "    id: inner\n" +
            "    focus: true\n" +
            "    property int innerHits: 0\n" +
            "    Keys.onPressed: { innerHits = innerHits + 1; event.accepted = true }\n" +
            "  }\n" +
            "}");
        boolean handled = v.dispatchKey(65, "a", true);
        assertTrue(handled);
        Item inner = root.children.get(0);
        assertEquals(1L, readProp(inner, "innerHits"));
        assertEquals(0L, readProp(root, "outerHits"));
    }

    @Test
    void unacceptedBubblesToParent() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  property int outerHits: 0\n" +
            "  Keys.onPressed: { outerHits = outerHits + 1; event.accepted = true }\n" +
            "  Item {\n" +
            "    focus: true\n" +
            "    property int innerHits: 0\n" +
            "    Keys.onPressed: innerHits = innerHits + 1\n" +
            "  }\n" +
            "}");
        boolean handled = v.dispatchKey(65, "a", true);
        assertTrue(handled);
        Item inner = root.children.get(0);
        assertEquals(1L, readProp(inner, "innerHits"));
        assertEquals(1L, readProp(root, "outerHits"));
    }

    @Test
    void returnPressedConvenienceSignal() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  focus: true\n" +
            "  property int returns: 0\n" +
            "  Keys.onReturnPressed: { returns = returns + 1; event.accepted = true }\n" +
            "}");
        boolean handled = v.dispatchKey(QmlView.KEY_ENTER, null, true);
        assertTrue(handled);
        assertEquals(1L, readProp(root, "returns"));
    }

    @Test
    void spacePressedConvenienceSignal() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  focus: true\n" +
            "  property int spaces: 0\n" +
            "  Keys.onSpacePressed: { spaces = spaces + 1; event.accepted = true }\n" +
            "}");
        boolean handled = v.dispatchKey(32, " ", true);
        assertTrue(handled);
        assertEquals(1L, readProp(root, "spaces"));
    }

    @Test
    void releasedHandlerFiresOnKeyUp() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  focus: true\n" +
            "  property int ups: 0\n" +
            "  Keys.onReleased: { ups = ups + 1; event.accepted = true }\n" +
            "}");
        boolean handled = v.dispatchKey(65, "a", false);
        assertTrue(handled);
        assertEquals(1L, readProp(root, "ups"));
    }

    @Test
    void eventTextExposedToHandler() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  focus: true\n" +
            "  property string typed: \"\"\n" +
            "  Keys.onPressed: { typed = typed + event.text; event.accepted = true }\n" +
            "}");
        v.dispatchKey(72, "h", true);
        v.dispatchKey(105, "i", true);
        assertEquals("hi", readProp(root, "typed"));
    }

    @Test
    void blankTapKeepsNonTextFocus() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  width: 400; height: 400\n" +
            "  Rectangle {\n" +
            "    focus: true\n" +
            "    width: 50; height: 50\n" +
            "    property int hits: 0\n" +
            "    Keys.onPressed: { hits = hits + 1; event.accepted = true }\n" +
            "  }\n" +
            "}");
        v.dispatchPointerDown(300, 300);
        v.dispatchPointerUp(300, 300);
        boolean handled = v.dispatchKey(65, "a", true);
        assertTrue(handled);
        assertEquals(1L, readProp(root.children.get(0), "hits"));
    }

    @Test
    void forceActiveFocusRegrabsAfterUnfocus() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  width: 400; height: 400\n" +
            "  Rectangle {\n" +
            "    focus: true\n" +
            "    width: 50; height: 50\n" +
            "    property int hits: 0\n" +
            "    Keys.onPressed: { hits = hits + 1; event.accepted = true }\n" +
            "  }\n" +
            "  TextInput { x: 100; y: 100; width: 80; height: 30 }\n" +
            "}");
        Item keysItem = root.children.get(0);
        v.setFocus(root.children.get(1));
        v.dispatchPointerDown(300, 300);
        v.dispatchPointerUp(300, 300);
        assertNull(v.focused());
        keysItem.forceActiveFocus();
        assertSame(keysItem, v.focused());
        assertTrue(v.dispatchKey(65, "a", true));
        assertEquals(1L, readProp(keysItem, "hits"));
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
