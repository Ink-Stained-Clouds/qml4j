package io.qml4j.compiler.bytecode.decl;

import io.qml4j.engine.QObject;
import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.qml4j.compiler.bytecode.asm.Bytecode.emitPropertyDefault;
import static io.qml4j.compiler.bytecode.asm.Descriptors.LIST_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.QOBJECT_DESC;
import static io.qml4j.compiler.bytecode.asm.Fields.findPropertyFieldOrNull;
import static io.qml4j.compiler.bytecode.asm.Fields.findSignalFieldOrNull;

// Pure-static helpers for collecting property declarations and aliases from an AST
// object node, and for emitting the bytecode that wires them into the constructor.
public final class PropertyDecls {

    private PropertyDecls() {}

    // Collects all PropertyDeclaration members of obj into DeclaredProp records.
    // Validates no duplicate names, no shadowing of existing signals, and that the
    // `default` modifier is only used on list aliases. Marks a record as isOverride
    // when the owner type already has an inherited Property field for that name.
    public static List<DeclaredProp> collectPropertyDecls(Ast.ObjectNode obj, Class<?> ownerType) {
        List<DeclaredProp> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Ast.ObjectMember m : obj.members) {
            if (!(m instanceof Ast.PropertyDeclaration)) continue;
            Ast.PropertyDeclaration pd = (Ast.PropertyDeclaration) m;
            // readonly/required are accepted as plain properties (v0 doesn't
            // enforce immutability or instantiation-time requirement). `default`
            // is only supported on a list alias (default property alias x: id.data).
            if (pd.isDefault && !"alias".equals(pd.typeName)) {
                throw new UnsupportedOperationException(
                    "default property modifier supported only on a list alias: " + pd.name);
            }
            if (!seen.add(pd.name)) {
                throw new IllegalArgumentException("duplicate property declaration: " + pd.name);
            }
            if (findSignalFieldOrNull(ownerType, pd.name) != null) {
                throw new IllegalArgumentException(
                    "property '" + pd.name + "' shadows existing signal on " + ownerType.getName());
            }
            // Redeclaring an inherited Property (Qt allows `property bool enabled`
            // on a type that already has enabled) -> treat as an override: no new
            // field, the initializer just sets the inherited one.
            boolean isOverride = !"alias".equals(pd.typeName)
                && findPropertyFieldOrNull(ownerType, pd.name) != null;
            out.add(new DeclaredProp(pd.name, pd.typeName, pd.initializer, pd.isDefault, isOverride));
        }
        return out;
    }

    // Parses a DeclaredProp of type "alias" into an AliasDecl, resolving whether
    // it is an object alias (id), a list alias (id.data / id.children), or a
    // property alias (id.property).
    public static AliasDecl parseAlias(DeclaredProp dp) {
        if (dp.initializer == null) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' requires initializer of form id.property");
        }
        if (!(dp.initializer instanceof Ast.ExpressionValue)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' initializer must be expression id.property");
        }
        Ast.Expression e = ((Ast.ExpressionValue) dp.initializer).expr;
        if (e instanceof Ast.IdentifierExpr) {
            // Object alias: `property alias foo: someId` exposes the object itself.
            return new AliasDecl(dp.name, ((Ast.IdentifierExpr) e).name, null, false, dp.isDefault);
        }
        if (!(e instanceof Ast.MemberExpr)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' must reference id or id.property, got "
                + e.getClass().getSimpleName());
        }
        Ast.MemberExpr m = (Ast.MemberExpr) e;
        if (!(m.target instanceof Ast.IdentifierExpr)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' must reference id.property (target must be id)");
        }
        boolean isList = "data".equals(m.property) || "children".equals(m.property);
        return new AliasDecl(dp.name, ((Ast.IdentifierExpr) m.target).name, m.property,
                             isList, dp.isDefault);
    }

    // Emits the constructor bytecode that assigns an alias field: list alias links
    // to the target's children list, object alias links to the target object, and
    // property alias links to the target's Property field.
    public static void emitAliasLink(MethodVisitor ctor, String componentInternal,
                                     String rootId, Class<? extends QObject> rootType,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, String> rootDeclaredProps,
                                     AliasDecl ad) {
        Class<? extends QObject> targetType = idTypes.get(ad.targetId);
        if (targetType == null) {
            throw new IllegalArgumentException(
                "property alias '" + ad.name + "' references unknown id: " + ad.targetId);
        }
        if (ad.isList) {
            // this.NAME = this.targetId.children  (share the inner container's list)
            Field childrenField;
            try {
                childrenField = targetType.getField("children");
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(
                    "list alias '" + ad.name + "' target '" + ad.targetId + "' has no children list");
            }
            String childrenOwner = Type.getInternalName(childrenField.getDeclaringClass());
            String targetInternal = Type.getInternalName(targetType);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, componentInternal, ad.targetId,
                                "L" + targetInternal + ";");
            ctor.visitFieldInsn(Opcodes.GETFIELD, childrenOwner, "children", LIST_DESC);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, ad.name, LIST_DESC);
            return;
        }
        if (ad.targetProperty == null) {
            // Object alias: this.NAME = this.targetId
            String targetInternal = Type.getInternalName(targetType);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, componentInternal, ad.targetId,
                                "L" + targetInternal + ";");
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, ad.name, QOBJECT_DESC);
            return;
        }
        String targetFieldOwner = resolveAliasTargetFieldOwner(
            ad, targetType, rootId, componentInternal, rootDeclaredProps);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        if (targetFieldOwner.equals(componentInternal)) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
        } else {
            String targetInternal = Type.getInternalName(targetType);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, componentInternal, ad.targetId,
                                "L" + targetInternal + ";");
        }
        ctor.visitFieldInsn(Opcodes.GETFIELD, targetFieldOwner, ad.targetProperty, PROPERTY_DESC);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, ad.name, PROPERTY_DESC);
    }

    // Resolves which class owns the target property field for a non-list, non-object
    // alias. Checks the target type's hierarchy first, then falls back to the root
    // component's declared props (for aliases that target the root component itself).
    public static String resolveAliasTargetFieldOwner(AliasDecl ad,
                                                      Class<? extends QObject> targetType,
                                                      String rootId, String componentInternal,
                                                      Map<String, String> rootDeclaredProps) {
        Field f = findPropertyFieldOrNull(targetType, ad.targetProperty);
        if (f != null) {
            return Type.getInternalName(f.getDeclaringClass());
        }
        if (rootId != null && rootId.equals(ad.targetId)
            && rootDeclaredProps.containsKey(ad.targetProperty)) {
            return componentInternal;
        }
        throw new IllegalArgumentException(
            "property alias '" + ad.name + "' target '" + ad.targetId + "." + ad.targetProperty
            + "' resolves to no Property field (v0 allows builtin or root-declared targets only)");
    }

    // Emits NEW Property(default) + PUTFIELD for a freshly declared property field.
    public static void emitInitDeclaredProperty(MethodVisitor ctor, int receiverLocal,
                                                String ownerInternal, DeclaredProp dp) {
        ctor.visitVarInsn(Opcodes.ALOAD, receiverLocal);
        ctor.visitTypeInsn(Opcodes.NEW, PROPERTY_INTERNAL);
        ctor.visitInsn(Opcodes.DUP);
        emitPropertyDefault(ctor, dp.typeName);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, PROPERTY_INTERNAL,
                             "<init>", "(Ljava/lang/Object;)V", false);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, ownerInternal, dp.name, PROPERTY_DESC);
    }
}
