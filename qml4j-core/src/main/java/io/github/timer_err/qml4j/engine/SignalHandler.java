package io.github.timer_err.qml4j.engine;

@FunctionalInterface
public interface SignalHandler {
    void invoke(Object[] args);
}
