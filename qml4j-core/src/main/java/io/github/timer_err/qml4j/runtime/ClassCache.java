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

    // Drop every entry whose key Class was defined by {@code cl}. Because entries hold
    // strong Class keys (and their Field/Method values reference the declaring Class),
    // a hot-reload that spins up a fresh per-document ClassLoader would otherwise pin
    // that loader — and all its generated component classes — in Metaspace forever.
    // Call this when the owning view/loader is disposed. Stock-type entries (loaded by
    // the engine's parent loader) are untouched, so they stay cached across reloads.
    public void evictLoadedBy(ClassLoader cl) {
        if (cl == null) return;
        map.keySet().removeIf(k -> k.getClassLoader() == cl);
    }
}
