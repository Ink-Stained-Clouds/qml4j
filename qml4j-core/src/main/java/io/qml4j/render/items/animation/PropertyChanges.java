package io.qml4j.render.items.animation;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.PropertyChangeSink;
import io.qml4j.engine.RuntimeHelpers;
import io.qml4j.engine.binding.Binding;
import io.qml4j.engine.binding.Property;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
            saveBaselineOnce(t, e.getKey());
            RuntimeHelpers.writeMember(t, e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Binding> e : bindings.entrySet()) {
            saveBaselineOnce(t, e.getKey());
            RuntimeHelpers.writeMember(t, e.getKey(), e.getValue().evaluate());
        }
    }

    // Baseline is captured once on the first apply and preserved across
    // revert/apply cycles. Re-sampling during an in-flight animation would
    // promote the mid-tween value to "original", trapping the property
    // partway between states after rapid toggles.
    private void saveBaselineOnce(Object t, String name) {
        if (saved.containsKey(name)) return;
        saved.put(name, RuntimeHelpers.readMember(t, name));
    }

    public Object targetValue() {
        return target.peek();
    }

    public Set<String> propertyNames() {
        Set<String> all = new LinkedHashSet<>(literals.keySet());
        all.addAll(bindings.keySet());
        return all;
    }

    public void revert() {
        Object t = target.peek();
        if (t == null) return;
        for (Map.Entry<String, Object> e : saved.entrySet()) {
            RuntimeHelpers.writeMember(t, e.getKey(), e.getValue());
        }
    }
}
