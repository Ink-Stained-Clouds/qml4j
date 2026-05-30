package io.qml4j.compiler;

import io.qml4j.engine.QObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TypeRegistry {

    public interface TypeResolver {
        Class<? extends QObject> resolve(String qmlName);
    }

    private final Map<String, Class<? extends QObject>> types = new HashMap<>();
    private TypeResolver resolver;
    private Set<String> aliases = Collections.emptySet();

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

    public TypeRegistry withAliases(Set<String> aliases) {
        this.aliases = aliases == null ? Collections.emptySet() : aliases;
        return this;
    }

    public Class<? extends QObject> resolve(String qmlName) {
        Class<? extends QObject> c = lookup(qmlName);
        if (c != null) return c;
        if (!aliases.isEmpty()) {
            int dot = qmlName.indexOf('.');
            if (dot > 0 && aliases.contains(qmlName.substring(0, dot))) {
                c = lookup(qmlName.substring(dot + 1));
                if (c != null) return c;
            }
        }
        throw new IllegalArgumentException("unknown QML type: " + qmlName);
    }

    private Class<? extends QObject> lookup(String qmlName) {
        Class<? extends QObject> c = types.get(qmlName);
        if (c != null) return c;
        if (resolver != null) {
            c = resolver.resolve(qmlName);
            if (c != null) {
                types.put(qmlName, c);
                return c;
            }
        }
        return null;
    }

    public boolean isRegistered(String qmlName) {
        return types.containsKey(qmlName);
    }
}
