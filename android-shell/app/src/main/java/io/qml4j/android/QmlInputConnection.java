package io.qml4j.android;

import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;

final class QmlInputConnection extends BaseInputConnection {

    private final QmlGLSurfaceView view;

    QmlInputConnection(QmlGLSurfaceView view, boolean fullEditor) {
        super(view, fullEditor);
        this.view = view;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        view.commitTextFromIme(text);
        return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        view.commitTextFromIme(text);
        return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        view.deleteFromIme(beforeLength);
        return true;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = event.getKeyCode();
            if (kc == KeyEvent.KEYCODE_DEL) {
                view.deleteFromIme(1);
                return true;
            }
            if (kc == KeyEvent.KEYCODE_ENTER || kc == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                view.performImeEnter();
                return true;
            }
            int u = event.getUnicodeChar();
            if (u != 0) {
                view.commitTextFromIme(new String(Character.toChars(u)));
                return true;
            }
        }
        return super.sendKeyEvent(event);
    }

    @Override
    public boolean performEditorAction(int actionCode) {
        view.performImeEnter();
        return true;
    }
}
