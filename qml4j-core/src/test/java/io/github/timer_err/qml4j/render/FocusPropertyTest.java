package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.input.TextInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// QML hands focus around by writing the property (`textInput.focus = false`), not by calling
// forceActiveFocus(). A write that does not reach the focus manager leaves activeFocus and the
// key-event target pointing at an item the scene no longer considers focused.
class FocusPropertyTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static Item twoInputs(QmlView v) {
        return v.load(
            "Item { width: 200; height: 100\n"
            + "  TextInput { focus: true; text: \"hello\" }\n"
            + "  TextInput { y: 40 }\n"
            + "}");
    }

    @Test
    void writingFocusFalseReleasesActiveFocus() {
        QmlView v = newView();
        Item a = twoInputs(v).children.get(0);
        a.focus.set(Boolean.FALSE);
        assertFalse(Boolean.TRUE.equals(a.activeFocus.peek()));
        assertNull(v.focused());
    }

    @Test
    void writingFocusTrueTakesActiveFocus() {
        QmlView v = newView();
        Item root = twoInputs(v);
        Item a = root.children.get(0);
        Item b = root.children.get(1);
        b.focus.set(Boolean.TRUE);
        assertSame(b, v.focused());
        assertTrue(Boolean.TRUE.equals(b.activeFocus.peek()));
        assertFalse(Boolean.TRUE.equals(a.activeFocus.peek()));
        assertFalse(Boolean.TRUE.equals(a.focus.peek()));
    }

    @Test
    void keysFollowTheFocusPropertyWrite() {
        QmlView v = newView();
        Item root = twoInputs(v);
        TextInput a = (TextInput) root.children.get(0);
        TextInput b = (TextInput) root.children.get(1);
        b.focus.set(Boolean.TRUE);
        v.dispatchKey(0, "x", true);
        assertEquals("hello", a.text.peek());
        assertEquals("x", b.text.peek());
    }

    @Test
    void clearingAnUnfocusedItemDoesNotStealFocus() {
        QmlView v = newView();
        Item root = twoInputs(v);
        Item a = root.children.get(0);
        Item b = root.children.get(1);
        b.focus.set(Boolean.FALSE);
        assertSame(a, v.focused());
    }

    // The reason this surfaced: PasswordEchoOnEdit re-masks in the focus manager's leave path,
    // so a field revealed by typing stayed readable after `focus = false`.
    @Test
    void passwordEchoOnEditRemasksWhenFocusPropertyIsCleared() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 100\n"
            + "  TextInput { focus: true; echoMode: 3 }\n"
            + "}");
        TextInput ti = (TextInput) root.children.get(0);
        v.dispatchKey(0, "s", true);
        assertTrue(ti.isEchoEditing(), "typing reveals a PasswordEchoOnEdit field");
        ti.focus.set(Boolean.FALSE);
        assertFalse(ti.isEchoEditing(), "leaving the field must re-mask it");
    }
}
