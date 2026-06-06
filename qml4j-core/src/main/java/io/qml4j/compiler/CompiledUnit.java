package io.qml4j.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CompiledUnit {

    private final String rootClassName;
    private final Map<String, byte[]> classes;

    public CompiledUnit(String rootClassName, Map<String, byte[]> classes) {
        this.rootClassName = rootClassName;
        this.classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
    }

    public String rootClassName() {
        return rootClassName;
    }

    public Map<String, byte[]> classes() {
        return classes;
    }

    public byte[] rootBytes() {
        return classes.get(rootClassName);
    }
}
