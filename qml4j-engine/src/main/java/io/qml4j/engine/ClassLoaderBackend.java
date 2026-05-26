package io.qml4j.engine;

public interface ClassLoaderBackend {
    Class<?> defineClass(String name, byte[] jvmBytecode);
}
