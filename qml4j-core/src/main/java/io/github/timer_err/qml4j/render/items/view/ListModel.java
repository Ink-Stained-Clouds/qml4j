package io.github.timer_err.qml4j.render.items.view;

import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.QmlDefaultList;
import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@QmlDefaultList("rows")
public class ListModel extends QObject {

    public final List<ListElement> rows = new ArrayList<>();
    public final Property<Number> count = new Property<>(0);
    public final Signal rowsInserted = new Signal();
    public final Signal rowsRemoved = new Signal();
    public final Signal rowsChanged = new Signal();

    public ListModel() {
        Runnable sync = this::syncCount;
        rowsInserted.connect(sync);
        rowsRemoved.connect(sync);
    }

    public void append(Object data) {
        rows.add(toElement(data));
        rowsInserted.emit();
    }

    public ListElement get(int i) {
        return rows.get(i);
    }

    public void remove(int i) {
        remove(i, 1);
    }

    public void remove(int i, int n) {
        int end = Math.min(i + n, rows.size());
        for (int k = end - 1; k >= i; k--) rows.remove(k);
        rowsRemoved.emit();
    }

    public void clear() {
        if (rows.isEmpty()) return;
        rows.clear();
        rowsRemoved.emit();
    }

    @SuppressWarnings("unused")
    public void set(int i, Object data) {
        rows.set(i, toElement(data));
        rowsChanged.emit();
    }

    // Qt ListModel.setProperty(index, role, value): set a single role on an existing row.
    public void setProperty(int i, String role, Object value) {
        if (i < 0 || i >= rows.size()) return;
        rows.get(i).put(role, value);
        rowsChanged.emit();
    }

    private void syncCount() {
        count.set(rows.size());
    }

    @SuppressWarnings("unchecked")
    private static ListElement toElement(Object data) {
        if (data instanceof ListElement) return (ListElement) data;
        ListElement el = new ListElement();
        if (data instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) data).entrySet()) {
                el.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else if (data != null) {
            el.put("modelData", data);
        }
        return el;
    }

    @SuppressWarnings("unused")
    public Map<String, Object> asMap(int i) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.putAll(rows.get(i));
        return copy;
    }
}
