package io.qml4j.engine;

import java.util.ArrayList;
import java.util.List;

public final class Signal {

    private final List<Runnable> handlers = new ArrayList<>();

    public void connect(Runnable handler) {
        handlers.add(handler);
    }

    public void disconnect(Runnable handler) {
        handlers.remove(handler);
    }

    public void emit() {
        for (Runnable h : new ArrayList<>(handlers)) {
            h.run();
        }
    }
}
