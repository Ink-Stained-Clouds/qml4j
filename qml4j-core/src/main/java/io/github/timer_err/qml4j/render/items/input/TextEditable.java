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

    // Whether the editor's model text may leave the process through the clipboard.
    // Defaults to true so existing implementations keep working unchanged; masked
    // editors override it, because a host has no other way to tell that the text
    // it is about to publish is a password.
    default boolean allowsClipboardCopy() {
        return true;
    }

    void emitTextChanged();

    boolean handleEnter();

    int caretIndexAt(float localX, float localY, Renderer renderer);

    int moveCaretVertical(int caret, int delta, Renderer renderer);
}
