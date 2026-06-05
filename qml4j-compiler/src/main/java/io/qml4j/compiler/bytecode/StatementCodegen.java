package io.qml4j.compiler.bytecode;

import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayDeque;
import java.util.Deque;

final class StatementCodegen {

    enum ReturnKind { VOID, OBJECT }

    private static final class LoopLabels {
        final Label continueL;
        final Label breakL;
        LoopLabels(Label continueL, Label breakL) {
            this.continueL = continueL;
            this.breakL = breakL;
        }
    }

    private final ExpressionCodegen expr;
    private final ReturnKind returnKind;
    private final Deque<LoopLabels> loopStack = new ArrayDeque<>();
    private int nextSlot;

    StatementCodegen(ExpressionCodegen expr, int firstSlot) {
        this(expr, firstSlot, ReturnKind.VOID);
    }

    StatementCodegen(ExpressionCodegen expr, int firstSlot, ReturnKind returnKind) {
        this.expr = expr;
        this.nextSlot = firstSlot;
        this.returnKind = returnKind;
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
        } else if (s instanceof Ast.WhileStmt) {
            emitWhile(mv, (Ast.WhileStmt) s);
        } else if (s instanceof Ast.ForStmt) {
            emitFor(mv, (Ast.ForStmt) s);
        } else if (s instanceof Ast.SwitchStmt) {
            emitSwitch(mv, (Ast.SwitchStmt) s);
        } else if (s instanceof Ast.BreakStmt) {
            emitBreak(mv);
        } else if (s instanceof Ast.ContinueStmt) {
            emitContinue(mv);
        } else if (s instanceof Ast.ReturnStmt) {
            emitReturn(mv, (Ast.ReturnStmt) s);
        } else if (s instanceof Ast.ForInStmt) {
            // for-in has no ASM lowering; it only reaches a handler/function whose
            // body the Rhino backend runs. Hitting it here means canHandle let a
            // for-in body fall through to ASM -- a routing bug, not a user error.
            throw new IllegalStateException("for-in requires the Rhino backend");
        } else {
            throw new IllegalStateException("unknown statement: " + s.getClass().getName());
        }
    }

    private void emitReturn(MethodVisitor mv, Ast.ReturnStmt r) {
        if (returnKind == ReturnKind.VOID) {
            if (r.value != null) {
                expr.emit(mv, r.value);
                mv.visitInsn(Opcodes.POP);
            }
            mv.visitInsn(Opcodes.RETURN);
        } else {
            if (r.value != null) {
                expr.emit(mv, r.value);
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
            }
            mv.visitInsn(Opcodes.ARETURN);
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

    private void emitWhile(MethodVisitor mv, Ast.WhileStmt w) {
        Label condL = new Label();
        Label endL = new Label();
        mv.visitLabel(condL);
        expr.emit(mv, w.cond);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, ExpressionCodegen.HELPERS_INTERNAL,
                           "truthy", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, endL);
        loopStack.push(new LoopLabels(condL, endL));
        emit(mv, w.body);
        loopStack.pop();
        mv.visitJumpInsn(Opcodes.GOTO, condL);
        mv.visitLabel(endL);
    }

    private void emitFor(MethodVisitor mv, Ast.ForStmt f) {
        if (f.init != null) emit(mv, f.init);
        Label condL = new Label();
        Label updateL = new Label();
        Label endL = new Label();
        mv.visitLabel(condL);
        if (f.cond != null) {
            expr.emit(mv, f.cond);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, ExpressionCodegen.HELPERS_INTERNAL,
                               "truthy", "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, endL);
        }
        loopStack.push(new LoopLabels(updateL, endL));
        emit(mv, f.body);
        loopStack.pop();
        mv.visitLabel(updateL);
        if (f.update != null) {
            expr.emit(mv, f.update);
            mv.visitInsn(Opcodes.POP);
        }
        mv.visitJumpInsn(Opcodes.GOTO, condL);
        mv.visitLabel(endL);
    }

    private void emitSwitch(MethodVisitor mv, Ast.SwitchStmt s) {
        expr.emit(mv, s.discriminant);
        int discSlot = nextSlot++;
        mv.visitVarInsn(Opcodes.ASTORE, discSlot);

        int n = s.clauses.size();
        Label[] clauseL = new Label[n];
        for (int i = 0; i < n; i++) clauseL[i] = new Label();
        Label endL = new Label();
        int defaultIdx = -1;

        // Dispatch: jump to the first case whose label === discriminant.
        for (int i = 0; i < n; i++) {
            Ast.SwitchClause c = s.clauses.get(i);
            if (c.label == null) { defaultIdx = i; continue; }
            mv.visitVarInsn(Opcodes.ALOAD, discSlot);
            expr.emit(mv, c.label);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, ExpressionCodegen.HELPERS_INTERNAL,
                               "eqStrict", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, ExpressionCodegen.HELPERS_INTERNAL,
                               "truthy", "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(Opcodes.IFNE, clauseL[i]);
        }
        mv.visitJumpInsn(Opcodes.GOTO, defaultIdx >= 0 ? clauseL[defaultIdx] : endL);

        // Bodies in source order; fall through to the next clause, break -> end.
        Label continueL = loopStack.isEmpty() ? null : loopStack.peek().continueL;
        loopStack.push(new LoopLabels(continueL, endL));
        for (int i = 0; i < n; i++) {
            mv.visitLabel(clauseL[i]);
            for (Ast.Statement st : s.clauses.get(i).body) emit(mv, st);
        }
        loopStack.pop();
        mv.visitLabel(endL);
    }

    private void emitBreak(MethodVisitor mv) {
        if (loopStack.isEmpty()) {
            throw new IllegalArgumentException("'break' used outside of a loop");
        }
        mv.visitJumpInsn(Opcodes.GOTO, loopStack.peek().breakL);
    }

    private void emitContinue(MethodVisitor mv) {
        if (loopStack.isEmpty()) {
            throw new IllegalArgumentException("'continue' used outside of a loop");
        }
        mv.visitJumpInsn(Opcodes.GOTO, loopStack.peek().continueL);
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
