package io.qml4j.compiler.bytecode;

import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class StatementCodegen {

    private final ExpressionCodegen expr;
    private int nextSlot;

    StatementCodegen(ExpressionCodegen expr, int firstSlot) {
        this.expr = expr;
        this.nextSlot = firstSlot;
    }

    void emit(MethodVisitor mv, Ast.Statement s) {
        if (s instanceof Ast.Block) {
            emitBlock(mv, (Ast.Block) s);
        } else if (s instanceof Ast.ExprStmt) {
            emitExprStmt(mv, (Ast.ExprStmt) s);
        } else if (s instanceof Ast.VarDecl) {
            emitVarDecl(mv, (Ast.VarDecl) s);
        } else if (s instanceof Ast.IfStmt) {
            emitIfStmt(mv, (Ast.IfStmt) s);
        } else {
            throw new IllegalStateException("unknown statement: " + s.getClass().getName());
        }
    }

    private void emitBlock(MethodVisitor mv, Ast.Block b) {
        for (Ast.Statement st : b.statements) {
            emit(mv, st);
        }
    }

    private void emitExprStmt(MethodVisitor mv, Ast.ExprStmt s) {
        expr.emit(mv, s.expr);
        mv.visitInsn(Opcodes.POP);
    }

    private void emitVarDecl(MethodVisitor mv, Ast.VarDecl v) {
        if (v.init != null) {
            expr.emit(mv, v.init);
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL);
        }
        int slot = nextSlot++;
        mv.visitVarInsn(Opcodes.ASTORE, slot);
        expr.localVars().put(v.name, slot);
    }

    private void emitIfStmt(MethodVisitor mv, Ast.IfStmt s) {
        Label elseL = new Label();
        Label endL = new Label();
        expr.emit(mv, s.cond);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, ExpressionCodegen.HELPERS_INTERNAL,
                           "truthy", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, s.elseBranch != null ? elseL : endL);
        emit(mv, s.thenBranch);
        if (s.elseBranch != null) {
            mv.visitJumpInsn(Opcodes.GOTO, endL);
            mv.visitLabel(elseL);
            emit(mv, s.elseBranch);
        }
        mv.visitLabel(endL);
    }
}
