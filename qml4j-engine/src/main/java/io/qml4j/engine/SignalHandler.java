package io.qml4j.engine;

@FunctionalInterface
public interface SignalHandler {
    void invoke(Object[] args);
}
