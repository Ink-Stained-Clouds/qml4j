package io.github.timer_err.qml4j.render.items.input;

import io.github.timer_err.qml4j.render.Renderer;

public interface TextEditable {
    String text();
    void setText(String t);

    int cursorPosition();
    void setCursorPosition(int p);

    int selectionStart();
    int selectionEnd();
    void setSelectionRange(int start, int end);

    int selectionAnchor();
    void setSelectionAnchor(int a);

    boolean readOnly();
    int maximumLength();

    void emitTextChanged();

    boolean handleEnter();

    int caretIndexAt(float localX, float localY, Renderer renderer);

    int moveCaretVertical(int caret, int delta, Renderer renderer);
}
