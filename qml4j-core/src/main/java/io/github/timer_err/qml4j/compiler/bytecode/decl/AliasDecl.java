package io.github.timer_err.qml4j.compiler.bytecode.decl;

// A parsed property alias: name -> targetId.targetProperty. Carries list/default
// flags so callers can emit the correct field type and default-list annotation.
public final class AliasDecl {
    public final String name;
    public final String targetId;
    public final String targetProperty;  // null for object aliases (property alias foo: someId)
    public final boolean isList;         // alias to id.data / id.children (a List field)
    public final boolean isDefault;      // default property alias -> the default child container

    @SuppressWarnings("unused")
    public AliasDecl(String name, String targetId, String targetProperty) {
        this(name, targetId, targetProperty, false, false);
    }

    public AliasDecl(String name, String targetId, String targetProperty,
                     boolean isList, boolean isDefault) {
        this.name = name;
        this.targetId = targetId;
        this.targetProperty = targetProperty;
        this.isList = isList;
        this.isDefault = isDefault;
    }
}
