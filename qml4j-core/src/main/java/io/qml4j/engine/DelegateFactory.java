package io.qml4j.engine;

public interface DelegateFactory {
    // parent is set on the delegate root before its bindings are flushed, so a
    // binding can resolve outer-scope names (e.g. the enclosing component's
    // properties) by walking the parent chain during that first evaluation.
    QObject create(int index, Object modelData, Object parent);
}
