package io.qml4j.compiler.bytecode.ast;

import io.qml4j.parser.ast.Ast;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

// Collects the free identifiers of a handler/function body -- the bare names it
// reads or writes that are not bound by a local `var`, a for/for-in loop variable,
// a signal/arrow parameter, or a nested arrow's parameters. The Rhino backend uses
// this to decide whether every bare name a body touches is one the runtime QmlScope
// can resolve (a scene id, a component property, a function); anything outside that
// set (an alias, a singleton, a value-type group field) keeps the body on ASM.
//
// Member/index property names and object-literal keys are not identifiers and are
// never collected. `var` is treated sequentially rather than hoisted: a use before
// its declaration is reported free, which only ever errs toward the ASM fallback.
public final class FreeIdentifiers {

    private FreeIdentifiers() {}

    public static Set<String> collect(Ast.Statement body, Set<String> initiallyBound) {
        Set<String> free = new LinkedHashSet<>();
        stmt(body, new HashSet<>(initiallyBound), free);
        return free;
    }

    private static void stmt(Ast.Statement s, Set<String> bound, Set<String> free) {
        if (s instanceof Ast.Block) {
            for (Ast.Statement c : ((Ast.Block) s).statements) stmt(c, bound, free);
        } else if (s instanceof Ast.ExprStmt) {
            expr(((Ast.ExprStmt) s).expr, bound, free);
        } else if (s instanceof Ast.VarDecl) {
            Ast.VarDecl v = (Ast.VarDecl) s;
            if (v.init != null) expr(v.init, bound, free);
            bound.add(v.name);
        } else if (s instanceof Ast.ReturnStmt) {
            Ast.Expression v = ((Ast.ReturnStmt) s).value;
            if (v != null) expr(v, bound, free);
        } else if (s instanceof Ast.IfStmt) {
            Ast.IfStmt i = (Ast.IfStmt) s;
            expr(i.cond, bound, free);
            stmt(i.thenBranch, bound, free);
            if (i.elseBranch != null) stmt(i.elseBranch, bound, free);
        } else if (s instanceof Ast.WhileStmt) {
            Ast.WhileStmt w = (Ast.WhileStmt) s;
            expr(w.cond, bound, free);
            stmt(w.body, bound, free);
        } else if (s instanceof Ast.ForStmt) {
            Ast.ForStmt f = (Ast.ForStmt) s;
            if (f.init != null) stmt(f.init, bound, free);
            if (f.cond != null) expr(f.cond, bound, free);
            if (f.update != null) expr(f.update, bound, free);
            stmt(f.body, bound, free);
        } else if (s instanceof Ast.ForInStmt) {
            Ast.ForInStmt f = (Ast.ForInStmt) s;
            bound.add(f.varName);
            expr(f.iterable, bound, free);
            stmt(f.body, bound, free);
        } else if (s instanceof Ast.SwitchStmt) {
            Ast.SwitchStmt sw = (Ast.SwitchStmt) s;
            expr(sw.discriminant, bound, free);
            for (Ast.SwitchClause cl : sw.clauses) {
                if (cl.label != null) expr(cl.label, bound, free);
                for (Ast.Statement c : cl.body) stmt(c, bound, free);
            }
        }
        // BreakStmt / ContinueStmt: no identifiers.
    }

    private static void expr(Ast.Expression e, Set<String> bound, Set<String> free) {
        if (e instanceof Ast.IdentifierExpr) {
            String n = ((Ast.IdentifierExpr) e).name;
            if (!bound.contains(n)) free.add(n);
        } else if (e instanceof Ast.MemberExpr) {
            expr(((Ast.MemberExpr) e).target, bound, free);
        } else if (e instanceof Ast.IndexExpr) {
            expr(((Ast.IndexExpr) e).target, bound, free);
            expr(((Ast.IndexExpr) e).index, bound, free);
        } else if (e instanceof Ast.ArrayLitExpr) {
            for (Ast.Expression x : ((Ast.ArrayLitExpr) e).elements) expr(x, bound, free);
        } else if (e instanceof Ast.ObjectLitExpr) {
            for (Ast.Expression x : ((Ast.ObjectLitExpr) e).values) expr(x, bound, free);
        } else if (e instanceof Ast.CallExpr) {
            expr(((Ast.CallExpr) e).callee, bound, free);
            for (Ast.Expression x : ((Ast.CallExpr) e).args) expr(x, bound, free);
        } else if (e instanceof Ast.UnaryExpr) {
            expr(((Ast.UnaryExpr) e).operand, bound, free);
        } else if (e instanceof Ast.BinaryExpr) {
            expr(((Ast.BinaryExpr) e).left, bound, free);
            expr(((Ast.BinaryExpr) e).right, bound, free);
        } else if (e instanceof Ast.AssignmentExpr) {
            expr(((Ast.AssignmentExpr) e).target, bound, free);
            expr(((Ast.AssignmentExpr) e).value, bound, free);
        } else if (e instanceof Ast.SpreadExpr) {
            expr(((Ast.SpreadExpr) e).target, bound, free);
        } else if (e instanceof Ast.TemplateLiteralExpr) {
            for (Ast.Expression x : ((Ast.TemplateLiteralExpr) e).exprs) expr(x, bound, free);
        } else if (e instanceof Ast.CondExpr) {
            expr(((Ast.CondExpr) e).cond, bound, free);
            expr(((Ast.CondExpr) e).thenBranch, bound, free);
            expr(((Ast.CondExpr) e).elseBranch, bound, free);
        } else if (e instanceof Ast.ArrowFunctionExpr) {
            Ast.ArrowFunctionExpr a = (Ast.ArrowFunctionExpr) e;
            Set<String> inner = new HashSet<>(bound);
            inner.addAll(a.paramNames);
            if (a.bodyExpr != null) expr(a.bodyExpr, inner, free);
            if (a.bodyBlock != null) stmt(a.bodyBlock, inner, free);
        }
        // LiteralExpr: no identifiers.
    }
}
