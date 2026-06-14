package io.github.timer_err.qml4j.engine;

import io.github.timer_err.qml4j.engine.binding.Binding;

public interface PropertyChangeSink {
    @SuppressWarnings("unused")
    void addChange(String name, Object value);
    @SuppressWarnings("unused")
    void addChangeBinding(String name, Binding binding);
}
