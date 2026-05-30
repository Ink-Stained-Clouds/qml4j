package io.qml4j.engine;

import java.util.HashMap;
import java.util.Map;

public abstract class QObject {
    private Map<String, Callable> __functions;

    public final void __putFunction(String name, Callable c) {
        if (__functions == null) __functions = new HashMap<>();
        __functions.put(name, c);
    }

    public final Callable __getFunction(String name) {
        return __functions == null ? null : __functions.get(name);
    }
}
