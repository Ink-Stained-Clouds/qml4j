package io.github.timer_err.qml4j.render.items.input;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Font;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.Renderer;
import io.github.timer_err.qml4j.render.Painter;

public class TextInput extends Item implements TextEditable {
    public static final int ECHO_UNKNOWN = -1;
    public static final int ECHO_NORMAL = 0;
    public static final int ECHO_NO_ECHO = 1;
    // Masked by the catch-all in Painter.echoDisplay rather than by name; kept so the four
    // Qt echo modes read as a complete set.
    @SuppressWarnings("unused")
    public static final int ECHO_PASSWORD = 2;
    public static final int ECHO_PASSWORD_ON_EDIT = 3;

    public final Property<String> text = new Property<>("");
    public final Property<String> color = new Property<>("#000000");
    public final Property<Number> fontSize = new Property<>(16);
    public final Property<Number> cursorPosition = new Property<>(0);
    public final Property<Number> selectionStart = new Property<>(0);
    public final Property<Number> selectionEnd = new Property<>(0);
    public final Property<String> selectionColor = new Property<>("#308cff");
    @SuppressWarnings("unused")
    public final Property<String> selectedTextColor = new Property<>("#ffffff");
    public final Property<Number> maximumLength = new Property<>(Integer.MAX_VALUE);
    public final Property<Boolean> readOnly = new Property<>(Boolean.FALSE);
    public final Property<Number> echoMode = new Property<>(0); // TextInput.Normal
    public final Property<String> passwordCharacter = new Property<>("•");
    @SuppressWarnings("unused")
    public final Property<Number> horizontalAlignment = new Property<>(1); // TextInput.AlignLeft
    @SuppressWarnings("unused")
    public final Property<Number> verticalAlignment = new Property<>(32);   // TextInput.AlignTop
    @SuppressWarnings("unused")
    public final Font font = new Font();

    public final Signal textChanged = new Signal();
    public final Signal accepted = new Signal();
    @SuppressWarnings("unused")
    public final Signal editingFinished = new Signal();

    public int selectionAnchor = -1;

    private boolean echoEditing;

    public TextInput() {
        wireContentInvalidation(text, color, fontSize, cursorPosition, selectionStart,
            selectionEnd, selectionColor, selectedTextColor, echoMode, passwordCharacter,
            horizontalAlignment, verticalAlignment,
            font.family, font.pixelSize, font.pointSize, font.weight, font.bold, font.italic);
    }

    @Override public String text() { return text.peek(); }
    @Override public void setText(String t) { text.setFromEdit(t); }
    @Override public int cursorPosition() { return cursorPosition.peekInt(); }
    @Override public void setCursorPosition(int p) { cursorPosition.set(p); }
    @Override public int selectionStart() { return selectionStart.peekInt(); }
    @Override public int selectionEnd() { return selectionEnd.peekInt(); }
    @Override public void setSelectionRange(int s, int e) {
        selectionStart.set(s);
        selectionEnd.set(e);
    }
    @Override public int selectionAnchor() { return selectionAnchor; }
    @Override public void setSelectionAnchor(int a) { selectionAnchor = a; }
    @Override public boolean readOnly() { return Boolean.TRUE.equals(readOnly.peek()); }

    // Qt's whitelist: QQuickTextInputPrivate::copy writes the clipboard only when
    // m_echoMode == Normal, so every other and every unrecognised value fails closed.
    @Override public boolean allowsClipboardCopy() {
        return echo() == ECHO_NORMAL;
    }

    // The echoMode ordinal, or ECHO_UNKNOWN for anything that is not one of the four.
    // Read through Object: a QML binding can land a Boolean or a String on this Property
    // despite its declared type, so the instanceof is load-bearing even though the static
    // type says it cannot fail -- reading it as Number throws instead.
    @SuppressWarnings({"ConstantValue", "RedundantCast"})
    public int echo() {
        Object m = echoMode.peek();
        if (!(m instanceof Number)) return ECHO_UNKNOWN;
        double d = ((Number) m).doubleValue();
        int i = (int) d;
        if (i != d || i < ECHO_NORMAL || i > ECHO_PASSWORD_ON_EDIT) return ECHO_UNKNOWN;
        return i;
    }

    // True while the plaintext of a PasswordEchoOnEdit field may be shown. Qt reveals on
    // the first text-producing key after focus is gained, not on focus itself
    // (QQuickTextInput's m_passwordEchoEditing), and clears it again when focus leaves.
    public boolean isEchoEditing() {
        return echoEditing;
    }

    public void beginEchoEditing() {
        echoEditing = true;
    }

    public void endEchoEditing() {
        echoEditing = false;
    }
    @Override public int maximumLength() { return maximumLength.peekInt(); }
    @Override public void emitTextChanged() { textChanged.emit(); }
    @Override public boolean handleEnter() { accepted.emit(); return true; }
    @Override public int caretIndexAt(float localX, float localY, Renderer r) {
        return r.caretIndexFor(this, localX);
    }
    @Override public int moveCaretVertical(int caret, int delta, Renderer r) {
        return caret;
    }

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        p.drawTextInput(this, w, h, alpha);
    }
}
