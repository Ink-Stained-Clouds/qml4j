package io.github.timer_err.qml4j.engine.classloader;

import java.util.LinkedHashMap;
import java.util.Map;

public interface ClassLoaderBackend {

    Class<?> defineClass(String name, byte[] jvmBytecode);

    default Map<String, Class<?>> defineClasses(Map<String, byte[]> classes) {
        Map<String, Class<?>> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            out.put(e.getKey(), defineClass(e.getKey(), e.getValue()));
        }
        return out;
    }
}
