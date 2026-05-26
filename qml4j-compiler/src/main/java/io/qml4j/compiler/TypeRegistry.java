package io.qml4j.compiler;

import io.qml4j.engine.QObject;

import java.util.HashMap;
import java.util.Map;

public final class TypeRegistry {

    private final Map<String, Class<? extends QObject>> types = new HashMap<>();

    public TypeRegistry register(String qmlName, Class<? extends QObject> klass) {
        types.put(qmlName, klass);
        return this;
    }

    public Class<? extends QObject> resolve(String qmlName) {
        Class<? extends QObject> c = types.get(qmlName);
        if (c == null) {
            throw new IllegalArgumentException("unknown QML type: " + qmlName);
        }
        return c;
    }

    public boolean isRegistered(String qmlName) {
        return types.containsKey(qmlName);
    }
}
