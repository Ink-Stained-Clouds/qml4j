package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.input.TextInput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// What a TextInput DISPLAYS and what it lets the clipboard TAKE have to be decided from one
// reading of echoMode, or a field can show plaintext while refusing to copy it (or worse,
// the reverse). These tests pin the two answers together.
class EchoModeDisplayTest {

    private static final String SECRET = "s3cret";

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static TextInput inputWithEchoMode(QmlView v, String echoMode) {
        Item root = v.load(
            "Item { width: 200; height: 100\n"
            + "  TextInput { focus: true; text: \"" + SECRET + "\"; echoMode: " + echoMode + " }\n"
            + "}");
        return (TextInput) root.children.get(0);
    }

    private static String displayed(TextInput ti) {
        try {
            Method m = Painter.class.getDeclaredMethod("echoDisplay", TextInput.class);
            m.setAccessible(true);
            return (String) m.invoke(null, ti);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("echoDisplay is the display contract under test", ex);
        }
    }

    @Test
    void normalShowsPlaintextAndAllowsCopy() {
        TextInput ti = inputWithEchoMode(newView(), "0");
        assertEquals(SECRET, displayed(ti));
        assertTrue(ti.allowsClipboardCopy(), "Normal is the one mode Qt lets copy");
    }

    @Test
    void noEchoShowsNothingAndRefusesCopy() {
        TextInput ti = inputWithEchoMode(newView(), "1");
        assertEquals("", displayed(ti));
        assertFalse(ti.allowsClipboardCopy());
    }

    @Test
    void passwordMasksEveryCharacterAndRefusesCopy() {
        TextInput ti = inputWithEchoMode(newView(), "2");
        assertEquals("••••••", displayed(ti));
        assertFalse(ti.allowsClipboardCopy());
    }

    // The mode that used to render plaintext while the clipboard refused it.
    @Test
    void passwordEchoOnEditMasksBeforeAnythingIsTyped() {
        TextInput ti = inputWithEchoMode(newView(), "3");
        assertEquals("••••••", displayed(ti), "focus alone must not reveal the text");
        assertFalse(ti.allowsClipboardCopy());
    }

    // Qt reveals on the first text-producing key after focus, not on focus itself.
    @Test
    void passwordEchoOnEditRevealsOnceTheUserTypes() {
        QmlView v = newView();
        TextInput ti = inputWithEchoMode(v, "3");
        ti.cursorPosition.set(SECRET.length());

        v.dispatchKey(0, "X", true);

        assertEquals(SECRET + "X", displayed(ti), "typing reveals what is being edited");
        assertFalse(ti.allowsClipboardCopy(), "revealed on screen is still not copyable");
    }

    @Test
    void passwordEchoOnEditMasksAgainWhenFocusLeaves() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 100\n"
            + "  TextInput { objectName: \"secret\"; focus: true; text: \"" + SECRET + "\"; echoMode: 3 }\n"
            + "  TextInput { objectName: \"other\"; text: \"plain\" }\n"
            + "}");
        TextInput secret = (TextInput) root.children.get(0);
        TextInput other = (TextInput) root.children.get(1);
        secret.cursorPosition.set(SECRET.length());
        v.dispatchKey(0, "X", true);
        assertEquals(SECRET + "X", displayed(secret), "precondition: revealed while editing");

        v.setFocus(other);

        assertEquals("•••••••", displayed(secret), "losing focus masks the field again");
        assertFalse(secret.isEchoEditing());
    }

    // A mode nobody implements must not be the one that shows the password. QML can assign
    // any number at runtime, so this is reachable without a compiler change.
    @Test
    void unrecognisedModesMaskRatherThanFallThroughToPlaintext() {
        for (String mode : new String[] {"4", "99", "-1"}) {
            TextInput ti = inputWithEchoMode(newView(), mode);
            assertEquals("••••••", displayed(ti), "echoMode " + mode + " must mask");
            assertFalse(ti.allowsClipboardCopy(), "echoMode " + mode + " must refuse copy");
            assertEquals(TextInput.ECHO_UNKNOWN, ti.echo(), "echoMode " + mode);
        }
    }

    // Each value reaches echoMode raw through the real compiler, which is the only way a
    // non-Number ever lands on a Property<Number>.
    @Test
    void nonNumericAndFractionalModesMask() {
        for (String literal : new String[] {"0.5", "-0.5", "null", "false", "NaN"}) {
            TextInput ti = inputWithEchoMode(newView(), literal);
            assertEquals(TextInput.ECHO_UNKNOWN, ti.echo(), "echoMode " + literal);
            assertEquals("••••••", displayed(ti), "echoMode " + literal + " must mask");
            assertFalse(ti.allowsClipboardCopy(), "echoMode " + literal + " must refuse copy");
        }
    }

    // Zero is Normal however it is spelled, so a binding that computes 0.0 or -0.0 does not
    // silently turn a plain field into a masked one.
    @Test
    void everySpellingOfZeroIsNormal() {
        QmlView v = newView();
        TextInput ti = inputWithEchoMode(v, "0");
        for (Number zero : new Number[] {0, 0L, 0.0d, -0.0d, 0.0f}) {
            ti.echoMode.set(zero);
            assertEquals(TextInput.ECHO_NORMAL, ti.echo(), "zero as " + zero.getClass().getSimpleName());
            assertEquals(SECRET, displayed(ti));
            assertTrue(ti.allowsClipboardCopy());
        }
    }

    // A "show password" toggle binds echoMode. Flipping it back must re-mask, even though a
    // binding write is not a focus change. Qt's setEchoMode clears the reveal the same way.
    @Test
    void changingEchoModeEndsAnyRevealInProgress() {
        QmlView v = newView();
        Item root = v.load(
            "Item { id: page; width: 200; height: 100\n"
            + "  property bool revealed: true\n"
            + "  TextInput { focus: true; echoMode: page.revealed ? 0 : 3 }\n"
            + "}");
        TextInput ti = (TextInput) root.children.get(0);
        v.dispatchKey(0, "h", true);
        v.dispatchKey(0, "i", true);
        assertEquals("hi", displayed(ti), "precondition: Normal shows plaintext");

        setBool(root, "revealed", false);

        assertEquals("••", displayed(ti), "flipping to PasswordEchoOnEdit must re-mask");
        assertFalse(ti.isEchoEditing());
    }

    // A keystroke the editor refuses changes nothing, so it must not reveal anything either.
    @Test
    void aRejectedInsertDoesNotRevealTheField() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 100\n"
            + "  TextInput { focus: true; text: \"" + SECRET + "\"; echoMode: 3;"
            + " maximumLength: 6 }\n"
            + "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.cursorPosition.set(SECRET.length());

        assertFalse(v.dispatchKey(0, "X", true), "the insert has no room");

        assertEquals(SECRET, ti.text.peek(), "text is unchanged");
        assertEquals("••••••", displayed(ti), "a refused keystroke must not reveal");
        assertFalse(ti.isEchoEditing());
    }

    // Qt reveals on typing, not on a paste: pasted clipboard content must stay masked.
    @Test
    void pastingDoesNotRevealTheField() {
        QmlView v = newView();
        v.setClipboard(new Clipboard() {
            @Override public String getText() { return "PASTED"; }
            @Override public void setText(String t) { }
        });
        TextInput ti = inputWithEchoMode(v, "3");
        ti.cursorPosition.set(SECRET.length());

        assertTrue(v.paste());

        assertEquals(SECRET + "PASTED", ti.text.peek(), "the paste landed");
        assertEquals("••••••••••••", displayed(ti), "a paste must not reveal");
        assertFalse(ti.isEchoEditing());
    }

    private static void setBool(Item root, String name, boolean value) {
        try {
            Object prop = root.getClass().getField(name).get(root);
            prop.getClass().getMethod("set", Object.class).invoke(prop, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("declared property " + name, ex);
        }
    }
}
