package io.qml4j.render.items;

import io.qml4j.engine.PropertyChangeSink;
import io.qml4j.engine.RuntimeHelpers;
import io.qml4j.engine.binding.Binding;
import io.qml4j.engine.binding.Property;

import java.util.LinkedHashMap;
import java.util.Map;

public class PropertyChanges extends Item implements PropertyChangeSink {
    public final Property<Object> target = new Property<>(null);
    private final Map<String, Object> literals = new LinkedHashMap<>();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<String, Object> saved = new LinkedHashMap<>();

    public PropertyChanges() {
        visible.set(Boolean.FALSE);
    }

    @Override public void addChange(String name, Object value) {
        literals.put(name, value);
    }

    @Override public void addChangeBinding(String name, Binding binding) {
        bindings.put(name, binding);
    }

    public void apply() {
        Object t = target.peek();
        if (t == null) return;
        for (Map.Entry<String, Object> e : literals.entrySet()) {
            saved.put(e.getKey(), RuntimeHelpers.readMember(t, e.getKey()));
            RuntimeHelpers.writeMember(t, e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Binding> e : bindings.entrySet()) {
            saved.put(e.getKey(), RuntimeHelpers.readMember(t, e.getKey()));
            RuntimeHelpers.writeMember(t, e.getKey(), e.getValue().evaluate());
        }
    }

    public void revert() {
        Object t = target.peek();
        if (t != null) {
            for (Map.Entry<String, Object> e : saved.entrySet()) {
                RuntimeHelpers.writeMember(t, e.getKey(), e.getValue());
            }
        }
        saved.clear();
    }
}
