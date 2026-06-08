package io.github.timer_err.qml4j.engine;

import io.github.timer_err.qml4j.engine.binding.Binding;

public interface PropertyChangeSink {
    void addChange(String name, Object value);
    void addChangeBinding(String name, Binding binding);
}
