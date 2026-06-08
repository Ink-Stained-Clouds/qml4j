package io.github.timer_err.qml4j.engine.classloader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JvmClassLoaderBackend implements ClassLoaderBackend {

    private final DynamicClassLoader loader;

    public JvmClassLoaderBackend() {
        this(JvmClassLoaderBackend.class.getClassLoader());
    }

    public JvmClassLoaderBackend(ClassLoader parent) {
        this.loader = new DynamicClassLoader(parent);
    }

    @Override
    public Class<?> defineClass(String name, byte[] jvmBytecode) {
        return loader.defineFromBytes(name, jvmBytecode);
    }

    public ClassLoader classLoader() {
        return loader;
    }

    // Carries this document's compiled-JS cache (ScriptCache) so Rhino's generated JS
    // classes -- compiled with this loader as parent -- live and die with the document.
    private static final class DynamicClassLoader extends ClassLoader implements ScriptCache {
        private final Map<String, Object> scripts = new ConcurrentHashMap<>();

        DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineFromBytes(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }

        @Override public Map<String, Object> jsScriptCache() {
            return scripts;
        }
    }
}
