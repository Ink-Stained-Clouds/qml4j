package io.qml4j.engine;

public interface SignalRelay {
    void connectSignal(String name, SignalHandler handler);
}
