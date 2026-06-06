package io.qml4j.compiler.bytecode.decl;

import io.qml4j.parser.ast.Ast;

// A property declaration entry collected from an object node's AST members. Carries
// the compile-time information needed to emit a new Property field or wire an
// inherited one: name, QML type name, optional initializer, and flags for the
// default-property and override (redeclares inherited) cases.
public final class DeclaredProp {
    public final String name;
    public final String typeName;
    public final Ast.Value initializer;
    public final boolean isDefault;
    public final boolean isOverride;  // redeclares an inherited Property -> init it, no new field

    public DeclaredProp(String name, String typeName, Ast.Value initializer) {
        this(name, typeName, initializer, false, false);
    }

    public DeclaredProp(String name, String typeName, Ast.Value initializer, boolean isDefault) {
        this(name, typeName, initializer, isDefault, false);
    }

    public DeclaredProp(String name, String typeName, Ast.Value initializer,
                        boolean isDefault, boolean isOverride) {
        this.name = name;
        this.typeName = typeName;
        this.initializer = initializer;
        this.isDefault = isDefault;
        this.isOverride = isOverride;
    }
}
