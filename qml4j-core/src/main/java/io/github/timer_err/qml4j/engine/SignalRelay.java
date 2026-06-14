package io.github.timer_err.qml4j.engine;

public interface SignalRelay {
    @SuppressWarnings("unused")
    void connectSignal(String name, SignalHandler handler);
}
