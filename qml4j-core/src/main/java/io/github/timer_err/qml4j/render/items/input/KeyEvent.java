package io.github.timer_err.qml4j.render.items.input;

public final class KeyEvent {
    public static final int SHIFT = 1;

    @SuppressWarnings("unused")
    public final int key;
    @SuppressWarnings("unused")
    public final String text;
    @SuppressWarnings("unused")
    public final int modifiers;
    public boolean accepted = false;

    public KeyEvent(int key, String text, int modifiers) {
        this.key = key;
        this.text = text == null ? "" : text;
        this.modifiers = modifiers;
    }
}
