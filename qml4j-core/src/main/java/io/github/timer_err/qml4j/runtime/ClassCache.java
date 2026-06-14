package io.github.timer_err.qml4j.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// Per-class memoization keyed on Class identity. Replaces java.lang.ClassValue, which
// is absent on HarmonyOS / Android runtimes below API 33 and crashes class init with
// NoClassDefFoundError there. Keying on the Class object (not its name) keeps entries
// from distinct per-component classloaders separate. Reads are lock-free; unlike
// ClassValue this holds strong key refs, which is harmless for the engine's finite,
// app-classloader-bound component classes.
public final class ClassCache<V> {

    private final ConcurrentHashMap<Class<?>, V> map = new ConcurrentHashMap<>();
    private final Function<Class<?>, V> compute;

    public ClassCache(Function<Class<?>, V> compute) {
        this.compute = compute;
    }

    public V get(Class<?> type) {
        return map.computeIfAbsent(type, compute);
    }
}
