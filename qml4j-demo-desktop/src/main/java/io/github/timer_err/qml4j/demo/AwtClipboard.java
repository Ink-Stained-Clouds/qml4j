package io.github.timer_err.qml4j.demo;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

import io.github.timer_err.qml4j.render.Clipboard;

final class AwtClipboard implements Clipboard {

    @Override
    // getTransferData is annotated non-null, but keep the guard defensively.
    @SuppressWarnings("ConstantValue")
    public String getText() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null || !t.isDataFlavorSupported(DataFlavor.stringFlavor)) return null;
            Object data = t.getTransferData(DataFlavor.stringFlavor);
            return data == null ? null : data.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void setText(String text) {
        try {
            StringSelection sel = new StringSelection(text == null ? "" : text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        } catch (Exception ignored) {}
    }
}
