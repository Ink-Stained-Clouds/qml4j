package io.qml4j.compiler.bytecode;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;
import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class ExpressionCodegen {

    static final String HELPERS_INTERNAL = "io/qml4j/engine/RuntimeHelpers";
    static final String PROPERTY_INTERNAL = "io/qml4j/engine/binding/Property";
    private static final String BINARY_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String UNARY_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";

    static final class AliasRef {
        final String targetId;
        final String targetProperty;
        AliasRef(String targetId, String targetProperty) {
            this.targetId = targetId;
            this.targetProperty = targetProperty;
        }
    }

    private final String outerInternal;
    private final String bindingInternal;
    private final Class<?> outerType;
    private final String componentInternal;
    private final Map<String, Class<? extends QObject>> idTypes;
    private final Map<String, Integer> signalParams;
    private final Map<String, Integer> localVars;
    private final Map<String, String> declaredProps;
    private final Map<String, AliasRef> aliases;

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType) {
        this(outerInternal, bindingInternal, outerType, null,
             Collections.<String, Class<? extends QObject>>emptyMap(),
             Collections.<String, Integer>emptyMap(),
             new LinkedHashMap<String, Integer>(),
             Collections.<String, String>emptyMap(),
             Collections.<String, AliasRef>emptyMap());
    }

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType,
                      String componentInternal,
                      Map<String, Class<? extends QObject>> idTypes) {
        this(outerInternal, bindingInternal, outerType, componentInternal, idTypes,
             Collections.<String, Integer>emptyMap(),
             new LinkedHashMap<String, Integer>(),
             Collections.<String, String>emptyMap(),
             Collections.<String, AliasRef>emptyMap());
    }

    static ExpressionCodegen forBinding(String outerInternal, String bindingInternal, Class<?> outerType,
                                        String componentInternal,
                                        Map<String, Class<? extends QObject>> idTypes,
                                        Map<String, String> declaredProps,
                                        Map<String, AliasRef> aliases) {
        return new ExpressionCodegen(outerInternal, bindingInternal, outerType, componentInternal, idTypes,
                                     Collections.<String, Integer>emptyMap(),
                                     new LinkedHashMap<String, Integer>(),
                                     declaredProps, aliases);
    }

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType,
                      String componentInternal,
                      Map<String, Class<? extends QObject>> idTypes,
                      Map<String, Integer> signalParams) {
        this(outerInternal, bindingInternal, outerType, componentInternal, idTypes,
             signalParams, new LinkedHashMap<String, Integer>(),
             Collections.<String, String>emptyMap(),
             Collections.<String, AliasRef>emptyMap());
    }

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType,
                      String componentInternal,
                      Map<String, Class<? extends QObject>> idTypes,
                      Map<String, Integer> signalParams,
                      Map<String, Integer> localVars) {
        this(outerInternal, bindingInternal, outerType, componentInternal, idTypes,
             signalParams, localVars, Collections.<String, String>emptyMap(),
             Collections.<String, AliasRef>emptyMap());
    }

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType,
                      String componentInternal,
                      Map<String, Class<? extends QObject>> idTypes,
                      Map<String, Integer> signalParams,
                      Map<String, Integer> localVars,
                      Map<String, String> declaredProps,
                      Map<String, AliasRef> aliases) {
        this.outerInternal = outerInternal;
        this.bindingInternal = bindingInternal;
        this.outerType = outerType;
        this.componentInternal = componentInternal;
        this.idTypes = idTypes != null ? idTypes : Collections.<String, Class<? extends QObject>>emptyMap();
        this.signalParams = signalParams != null ? signalParams : Collections.<String, Integer>emptyMap();
        this.localVars = localVars != null ? localVars : new LinkedHashMap<String, Integer>();
        this.declaredProps = declaredProps != null ? declaredProps : Collections.<String, String>emptyMap();
        this.aliases = aliases != null ? aliases : Collections.<String, AliasRef>emptyMap();
    }

    Map<String, Integer> localVars() { return localVars; }

    void emit(MethodVisitor mv, Ast.Expression e) {
        if (e instanceof Ast.LiteralExpr) {
            emitLiteral(mv, (Ast.LiteralExpr) e);
        } else if (e instanceof Ast.IdentifierExpr) {
            emitIdentifier(mv, (Ast.IdentifierExpr) e);
        } else if (e instanceof Ast.MemberExpr) {
            emitMember(mv, (Ast.MemberExpr) e);
        } else if (e instanceof Ast.UnaryExpr) {
            emitUnary(mv, (Ast.UnaryExpr) e);
        } else if (e instanceof Ast.BinaryExpr) {
            emitBinary(mv, (Ast.BinaryExpr) e);
        } else if (e instanceof Ast.CondExpr) {
            emitCond(mv, (Ast.CondExpr) e);
        } else if (e instanceof Ast.AssignmentExpr) {
            emitAssignment(mv, (Ast.AssignmentExpr) e);
        } else if (e instanceof Ast.CallExpr) {
            emitCall(mv, (Ast.CallExpr) e);
        } else {
            throw new IllegalStateException("unknown expression: " + e.getClass().getName());
        }
    }

    private void emitLiteral(MethodVisitor mv, Ast.LiteralExpr lit) {
        switch (lit.kind) {
            case INT:
                mv.visitLdcInsn((Long) lit.value);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long",
                                   "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case FLOAT:
                mv.visitLdcInsn((Double) lit.value);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double",
                                   "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case STRING:
                mv.visitLdcInsn((String) lit.value);
                break;
            case BOOL:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean",
                                  ((Boolean) lit.value) ? "TRUE" : "FALSE",
                                  "Ljava/lang/Boolean;");
                break;
            case NULL:
            case UNDEFINED:
                mv.visitInsn(Opcodes.ACONST_NULL);
                break;
            default:
                throw new IllegalStateException("literal kind: " + lit.kind);
        }
    }

    private void emitIdentifier(MethodVisitor mv, Ast.IdentifierExpr id) {
        Integer localSlot = localVars.get(id.name);
        if (localSlot != null) {
            mv.visitVarInsn(Opcodes.ALOAD, localSlot);
            return;
        }
        Integer paramSlot = signalParams.get(id.name);
        if (paramSlot != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            pushInt(mv, paramSlot);
            mv.visitInsn(Opcodes.AALOAD);
            return;
        }
        AliasRef alias = aliases.get(id.name);
        if (alias != null) {
            emit(mv, new Ast.MemberExpr(new Ast.IdentifierExpr(alias.targetId), alias.targetProperty));
            return;
        }
        String declaredOwner = declaredProps.get(id.name);
        if (declaredOwner != null) {
            loadDeclaredPropertyField(mv, id.name, declaredOwner);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                               "get", "()Ljava/lang/Object;", false);
            return;
        }
        Class<? extends QObject> idType = idTypes.get(id.name);
        if (idType != null && componentInternal != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "root", "L" + componentInternal + ";");
            mv.visitFieldInsn(Opcodes.GETFIELD, componentInternal, id.name,
                              "L" + Type.getInternalName(idType) + ";");
            return;
        }
        Field f = findPropertyField(outerType, id.name);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "outer", "L" + outerInternal + ";");
        mv.visitFieldInsn(Opcodes.GETFIELD, declOwner, id.name, "L" + PROPERTY_INTERNAL + ";");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                           "get", "()Ljava/lang/Object;", false);
    }

    private void loadDeclaredPropertyField(MethodVisitor mv, String name, String ownerInternal) {
        if (componentInternal != null && ownerInternal.equals(componentInternal)) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "root",
                              "L" + componentInternal + ";");
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "outer",
                              "L" + outerInternal + ";");
            mv.visitTypeInsn(Opcodes.CHECKCAST, ownerInternal);
        }
        mv.visitFieldInsn(Opcodes.GETFIELD, ownerInternal, name, "L" + PROPERTY_INTERNAL + ";");
    }

    private void emitMember(MethodVisitor mv, Ast.MemberExpr m) {
        emit(mv, m.target);
        mv.visitLdcInsn(m.property);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "readMember",
                           "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
    }

    private void emitUnary(MethodVisitor mv, Ast.UnaryExpr u) {
        emit(mv, u.operand);
        String helper;
        switch (u.op) {
            case "-": helper = "neg"; break;
            case "+": helper = "pos"; break;
            case "!": helper = "not"; break;
            default: throw new IllegalStateException("unary op: " + u.op);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, helper, UNARY_DESC, false);
    }

    private void emitBinary(MethodVisitor mv, Ast.BinaryExpr b) {
        emit(mv, b.left);
        emit(mv, b.right);
        String helper = binaryHelperName(b.op);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL, helper, BINARY_DESC, false);
    }

    private static String binaryHelperName(String op) {
        switch (op) {
            case "+":   return "add";
            case "-":   return "sub";
            case "*":   return "mul";
            case "/":   return "div";
            case "%":   return "mod";
            case "==":  return "eq";
            case "!=":  return "neq";
            case "===": return "eqStrict";
            case "!==": return "neqStrict";
            case "<":   return "lt";
            case "<=":  return "le";
            case ">":   return "gt";
            case ">=":  return "ge";
            case "&&":  return "and";
            case "||":  return "or";
            case "&":   return "bitAnd";
            case "|":   return "bitOr";
            case "^":   return "bitXor";
            default: throw new IllegalStateException("binary op: " + op);
        }
    }

    private void emitAssignment(MethodVisitor mv, Ast.AssignmentExpr a) {
        if (a.target instanceof Ast.MemberExpr) {
            Ast.MemberExpr m = (Ast.MemberExpr) a.target;
            emit(mv, m.target);
            mv.visitLdcInsn(m.property);
            emit(mv, a.value);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                               "writeMember",
                               "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
                               false);
        } else if (a.target instanceof Ast.IdentifierExpr) {
            String name = ((Ast.IdentifierExpr) a.target).name;
            Integer localSlot = localVars.get(name);
            if (localSlot != null) {
                emit(mv, a.value);
                mv.visitInsn(Opcodes.DUP);
                mv.visitVarInsn(Opcodes.ASTORE, localSlot);
                return;
            }
            AliasRef alias = aliases.get(name);
            if (alias != null) {
                Ast.MemberExpr m = new Ast.MemberExpr(new Ast.IdentifierExpr(alias.targetId), alias.targetProperty);
                emitAssignment(mv, new Ast.AssignmentExpr(m, a.value));
                return;
            }
            String declaredOwner = declaredProps.get(name);
            if (declaredOwner != null) {
                loadDeclaredPropertyField(mv, name, declaredOwner);
                emit(mv, a.value);
                mv.visitInsn(Opcodes.DUP_X1);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                   "set", "(Ljava/lang/Object;)V", false);
                return;
            }
            Field f = findPropertyField(outerType, name);
            String declOwner = Type.getInternalName(f.getDeclaringClass());
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "outer", "L" + outerInternal + ";");
            mv.visitFieldInsn(Opcodes.GETFIELD, declOwner, name, "L" + PROPERTY_INTERNAL + ";");
            emit(mv, a.value);
            mv.visitInsn(Opcodes.DUP_X1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                               "set", "(Ljava/lang/Object;)V", false);
        } else {
            throw new UnsupportedOperationException(
                "assignment target must be identifier or member access, got " + a.target.getClass().getSimpleName());
        }
    }

    private void emitCall(MethodVisitor mv, Ast.CallExpr c) {
        if (!(c.callee instanceof Ast.MemberExpr)) {
            throw new UnsupportedOperationException(
                "only method calls of the form receiver.method(...) are supported");
        }
        Ast.MemberExpr m = (Ast.MemberExpr) c.callee;
        emit(mv, m.target);
        mv.visitLdcInsn(m.property);
        pushInt(mv, c.args.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < c.args.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            emit(mv, c.args.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "callMethod",
                           "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                           false);
    }

    private void emitCond(MethodVisitor mv, Ast.CondExpr c) {
        Label elseL = new Label();
        Label endL = new Label();
        emit(mv, c.cond);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_INTERNAL,
                           "truthy", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, elseL);
        emit(mv, c.thenBranch);
        mv.visitJumpInsn(Opcodes.GOTO, endL);
        mv.visitLabel(elseL);
        emit(mv, c.elseBranch);
        mv.visitLabel(endL);
    }

    private static void pushInt(MethodVisitor mv, int v) {
        if (v >= 0 && v <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + v);
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, v);
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, v);
        } else {
            mv.visitLdcInsn(v);
        }
    }

    private static Field findPropertyField(Class<?> outerType, String name) {
        Field f;
        try {
            f = outerType.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                "identifier '" + name + "' has no matching Property field on "
                + outerType.getName());
        }
        if (!Property.class.isAssignableFrom(f.getType())) {
            throw new IllegalArgumentException(
                "field '" + name + "' on " + outerType.getName() + " is not a Property");
        }
        return f;
    }
}
