package io.github.timer_err.qml4j.render.items.dialog;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;

// QtQuick.Dialogs FileDialog. v0 stub: properties + open()/close() and the
// accepted/rejected signals so a document loads and its handlers compile. The native
// file picker is a host concern (not shown here), so open() is a no-op.
public class FileDialog extends Item {
    public final Property<String> title = new Property<>("");
    public final Property<Object> nameFilters = new Property<>(null);
    public final Property<Object> selectedFile = new Property<>(null);
    public final Property<Object> currentFile = new Property<>(null);
    public final Property<Object> currentFolder = new Property<>(null);
    public final Property<String> fileMode = new Property<>("OpenFile");
    public final Signal accepted = new Signal();
    public final Signal rejected = new Signal();

    public FileDialog() {
        visible.set(Boolean.FALSE);
    }

    public void open() {}

    public void close() {}
}
