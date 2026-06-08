package io.github.timer_err.qml4j.compiler.bytecode.asm;

import io.github.timer_err.qml4j.parser.ast.Ast;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

// Low-level bytecode emission primitives shared across the emitters: building a
// String[] on the stack, and pushing a boxed default / a QML literal value.
public final class Bytecode {

    private Bytecode() {}

    public static void pushStringArray(MethodVisitor mv, List<String> items) {
        mv.visitLdcInsn(items.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int i = 0; i < items.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(i);
            mv.visitLdcInsn(items.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    public static void emitPropertyDefault(MethodVisitor mv, String typeName) {
        switch (typeName) {
            case "int":
            case "integer":
                mv.visitLdcInsn(0L);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long",
                                   "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case "real":
            case "double":
            case "float":
                mv.visitLdcInsn(0.0d);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double",
                                   "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case "string":
                mv.visitLdcInsn("");
                break;
            case "bool":
            case "boolean":
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean",
                                  "FALSE", "Ljava/lang/Boolean;");
                break;
            default:
                mv.visitInsn(Opcodes.ACONST_NULL);
        }
    }

    public static void loadLiteral(MethodVisitor mv, Ast.LiteralExpr lit) {
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
}
