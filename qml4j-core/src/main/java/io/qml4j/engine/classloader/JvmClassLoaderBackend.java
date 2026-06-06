package io.qml4j.engine.classloader;

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

    private static final class DynamicClassLoader extends ClassLoader {
        DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineFromBytes(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
