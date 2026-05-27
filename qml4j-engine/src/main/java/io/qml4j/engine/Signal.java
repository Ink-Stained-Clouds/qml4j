package io.qml4j.engine;

import java.util.ArrayList;
import java.util.List;

public final class Signal {

    private static final Object[] EMPTY = new Object[0];

    private final List<SignalHandler> handlers = new ArrayList<>();

    public void connect(SignalHandler handler) {
        handlers.add(handler);
    }

    public void connect(Runnable handler) {
        handlers.add(args -> handler.run());
    }

    public void disconnect(SignalHandler handler) {
        handlers.remove(handler);
    }

    public void emit() {
        emit(EMPTY);
    }

    public void emit(Object... args) {
        Object[] payload = args != null ? args : EMPTY;
        for (SignalHandler h : new ArrayList<>(handlers)) {
            h.invoke(payload);
        }
    }
}
