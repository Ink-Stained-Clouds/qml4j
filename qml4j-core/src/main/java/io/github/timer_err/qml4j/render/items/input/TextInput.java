package io.github.timer_err.qml4j.render.items.input;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Font;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.Renderer;
import io.github.timer_err.qml4j.render.Painter;

public class TextInput extends Item implements TextEditable {
    public final Property<String> text = new Property<>("");
    public final Property<String> color = new Property<>("#000000");
    public final Property<Number> fontSize = new Property<>(16);
    public final Property<Number> cursorPosition = new Property<>(0);
    public final Property<Number> selectionStart = new Property<>(0);
    public final Property<Number> selectionEnd = new Property<>(0);
    public final Property<String> selectionColor = new Property<>("#308cff");
    public final Property<String> selectedTextColor = new Property<>("#ffffff");
    public final Property<Number> maximumLength = new Property<>(Integer.MAX_VALUE);
    public final Property<Boolean> readOnly = new Property<>(Boolean.FALSE);
    public final Property<Number> echoMode = new Property<>(0); // TextInput.Normal
    public final Property<String> passwordCharacter = new Property<>("•");
    public final Property<Number> horizontalAlignment = new Property<>(1); // TextInput.AlignLeft
    public final Property<Number> verticalAlignment = new Property<>(32);   // TextInput.AlignTop
    public final Font font = new Font();

    public final Signal textChanged = new Signal();
    public final Signal accepted = new Signal();
    public final Signal editingFinished = new Signal();

    public int selectionAnchor = -1;

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
