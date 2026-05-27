package io.qml4j.compiler.bytecode;

import io.qml4j.engine.binding.Property;
import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Field;

final class ExpressionCodegen {

    static final String HELPERS_INTERNAL = "io/qml4j/engine/RuntimeHelpers";
    static final String PROPERTY_INTERNAL = "io/qml4j/engine/binding/Property";
    private static final String BINARY_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String UNARY_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";

    private final String outerInternal;
    private final String bindingInternal;
    private final Class<?> outerType;

    ExpressionCodegen(String outerInternal, String bindingInternal, Class<?> outerType) {
        this.outerInternal = outerInternal;
        this.bindingInternal = bindingInternal;
        this.outerType = outerType;
    }

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
            throw new UnsupportedOperationException("M5: function calls not supported");
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
        Field f = findPropertyField(outerType, id.name);
        String declOwner = org.objectweb.asm.Type.getInternalName(f.getDeclaringClass());
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, bindingInternal, "outer", "L" + outerInternal + ";");
        mv.visitFieldInsn(Opcodes.GETFIELD, declOwner, id.name, "L" + PROPERTY_INTERNAL + ";");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                           "get", "()Ljava/lang/Object;", false);
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
            Field f = findPropertyField(outerType, name);
            String declOwner = org.objectweb.asm.Type.getInternalName(f.getDeclaringClass());
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
