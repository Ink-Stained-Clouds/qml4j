package io.qml4j.compiler.bytecode;

// A property alias's target: the id whose property the alias forwards to. Threaded
// per-binding to the Rhino backend, which expands `aliasName` to a read/write of
// `targetId.targetProperty` at runtime (see QmlScope).
final class AliasRef {
    final String targetId;
    final String targetProperty;

    AliasRef(String targetId, String targetProperty) {
        this.targetId = targetId;
        this.targetProperty = targetProperty;
    }
}
