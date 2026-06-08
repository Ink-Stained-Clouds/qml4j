package io.github.timer_err.qml4j.render.items.view;

import io.github.timer_err.qml4j.engine.PropertyChangeSink;
import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.binding.Binding;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ListElement extends QObject
        implements PropertyChangeSink, Map<String, Object> {

    private final LinkedHashMap<String, Object> roles = new LinkedHashMap<>();

    @Override public void addChange(String name, Object value) {
        roles.put(name, value);
    }

    @Override public void addChangeBinding(String name, Binding binding) {
        roles.put(name, binding.evaluate());
    }

    @Override public int size() { return roles.size(); }
    @Override public boolean isEmpty() { return roles.isEmpty(); }
    @Override public boolean containsKey(Object key) { return roles.containsKey(key); }
    @Override public boolean containsValue(Object value) { return roles.containsValue(value); }
    @Override public Object get(Object key) { return roles.get(key); }
    @Override public Object put(String key, Object value) { return roles.put(key, value); }
    @Override public Object remove(Object key) { return roles.remove(key); }
    @Override public void putAll(Map<? extends String, ?> m) { roles.putAll(m); }
    @Override public void clear() { roles.clear(); }
    @Override public Set<String> keySet() { return roles.keySet(); }
    @Override public Collection<Object> values() { return roles.values(); }
    @Override public Set<Entry<String, Object>> entrySet() { return roles.entrySet(); }
}
