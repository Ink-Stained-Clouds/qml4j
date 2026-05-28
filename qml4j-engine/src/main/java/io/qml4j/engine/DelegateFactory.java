package io.qml4j.engine;

public interface DelegateFactory {
    QObject create(int index, Object modelData);
}
