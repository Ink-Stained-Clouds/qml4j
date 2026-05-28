package io.qml4j.compiler;

import io.qml4j.engine.QObject;

import java.util.HashMap;
import java.util.Map;

public final class TypeRegistry {

    public interface TypeResolver {
        Class<? extends QObject> resolve(String qmlName);
    }

    private final Map<String, Class<? extends QObject>> types = new HashMap<>();
    private TypeResolver resolver;

    public TypeRegistry register(String qmlName, Class<? extends QObject> klass) {
        types.put(qmlName, klass);
        return this;
    }

    public TypeRegistry copy() {
        TypeRegistry t = new TypeRegistry();
        t.types.putAll(this.types);
        return t;
    }

    public TypeRegistry withResolver(TypeResolver r) {
        this.resolver = r;
        return this;
    }

    public Class<? extends QObject> resolve(String qmlName) {
        Class<? extends QObject> c = types.get(qmlName);
        if (c != null) return c;
        if (resolver != null) {
            c = resolver.resolve(qmlName);
            if (c != null) {
                types.put(qmlName, c);
                return c;
            }
        }
        throw new IllegalArgumentException("unknown QML type: " + qmlName);
    }

    public boolean isRegistered(String qmlName) {
        return types.containsKey(qmlName);
    }
}
