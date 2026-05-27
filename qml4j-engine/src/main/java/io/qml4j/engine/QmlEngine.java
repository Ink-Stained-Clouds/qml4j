package io.qml4j.engine;

import io.qml4j.engine.classloader.ClassLoaderBackend;
import io.qml4j.engine.classloader.JvmClassLoaderBackend;

public final class QmlEngine {

    private final ClassLoaderBackend backend;
    private final Context rootContext = new Context();

    public QmlEngine() {
        this(new JvmClassLoaderBackend());
    }

    public QmlEngine(ClassLoaderBackend backend) {
        this.backend = backend;
    }

    public ClassLoaderBackend backend() {
        return backend;
    }

    public Context rootContext() {
        return rootContext;
    }
}
