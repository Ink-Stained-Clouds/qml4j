package io.qml4j.compiler.bytecode;

import io.qml4j.parser.ast.Ast;

import java.util.function.Predicate;

// A structural "does any node match?" walk over a handler/function body -- statements
// and the expressions they contain, descending into nested arrow bodies. Used to spot
// constructs that decide backend routing: a for-in the ASM codegen cannot lower, or a
// Qt reactive helper the Rhino globals do not yet provide.
final class AstScan {

    private AstScan() {}

    // True if the body contains a for-in. ASM has no for-in lowering, so such a body
    // must run on Rhino.
    static boolean containsForIn(Ast.Statement body) {
        return scan(body, n -> n instanceof Ast.ForInStmt);
    }

    // True if the body calls Qt.binding. The Rhino globals do not yet bridge the lazy
    // Qt.binding (Qt.callLater is bridged), so such a body stays on ASM.
    static boolean usesDeferredQtHelper(Ast.Statement body) {
        return scan(body, AstScan::isDeferredQtMember);
    }

    private static boolean isDeferredQtMember(Object n) {
        if (!(n instanceof Ast.MemberExpr)) return false;
        Ast.MemberExpr m = (Ast.MemberExpr) n;
        return m.target instanceof Ast.IdentifierExpr
            && "Qt".equals(((Ast.IdentifierExpr) m.target).name)
            && "binding".equals(m.property);
    }

    private static boolean scan(Ast.Statement s, Predicate<Object> p) {
        if (s == null) return false;
        if (p.test(s)) return true;
        if (s instanceof Ast.Block) {
            for (Ast.Statement c : ((Ast.Block) s).statements) if (scan(c, p)) return true;
        } else if (s instanceof Ast.ExprStmt) {
            return scan(((Ast.ExprStmt) s).expr, p);
        } else if (s instanceof Ast.VarDecl) {
            return scan(((Ast.VarDecl) s).init, p);
        } else if (s instanceof Ast.ReturnStmt) {
            return scan(((Ast.ReturnStmt) s).value, p);
        } else if (s instanceof Ast.IfStmt) {
            Ast.IfStmt i = (Ast.IfStmt) s;
            return scan(i.cond, p) || scan(i.thenBranch, p) || scan(i.elseBranch, p);
        } else if (s instanceof Ast.WhileStmt) {
            Ast.WhileStmt w = (Ast.WhileStmt) s;
            return scan(w.cond, p) || scan(w.body, p);
        } else if (s instanceof Ast.ForStmt) {
            Ast.ForStmt f = (Ast.ForStmt) s;
            return scan(f.init, p) || scan(f.cond, p) || scan(f.update, p) || scan(f.body, p);
        } else if (s instanceof Ast.ForInStmt) {
            Ast.ForInStmt f = (Ast.ForInStmt) s;
            return scan(f.iterable, p) || scan(f.body, p);
        } else if (s instanceof Ast.SwitchStmt) {
            Ast.SwitchStmt sw = (Ast.SwitchStmt) s;
            if (scan(sw.discriminant, p)) return true;
            for (Ast.SwitchClause cl : sw.clauses) {
                if (scan(cl.label, p)) return true;
                for (Ast.Statement c : cl.body) if (scan(c, p)) return true;
            }
        }
        return false;
    }

    private static boolean scan(Ast.Expression e, Predicate<Object> p) {
        if (e == null) return false;
        if (p.test(e)) return true;
        if (e instanceof Ast.MemberExpr) {
            return scan(((Ast.MemberExpr) e).target, p);
        } else if (e instanceof Ast.IndexExpr) {
            return scan(((Ast.IndexExpr) e).target, p) || scan(((Ast.IndexExpr) e).index, p);
        } else if (e instanceof Ast.ArrayLitExpr) {
            for (Ast.Expression x : ((Ast.ArrayLitExpr) e).elements) if (scan(x, p)) return true;
        } else if (e instanceof Ast.ObjectLitExpr) {
            for (Ast.Expression x : ((Ast.ObjectLitExpr) e).values) if (scan(x, p)) return true;
        } else if (e instanceof Ast.CallExpr) {
            if (scan(((Ast.CallExpr) e).callee, p)) return true;
            for (Ast.Expression x : ((Ast.CallExpr) e).args) if (scan(x, p)) return true;
        } else if (e instanceof Ast.UnaryExpr) {
            return scan(((Ast.UnaryExpr) e).operand, p);
        } else if (e instanceof Ast.BinaryExpr) {
            return scan(((Ast.BinaryExpr) e).left, p) || scan(((Ast.BinaryExpr) e).right, p);
        } else if (e instanceof Ast.AssignmentExpr) {
            return scan(((Ast.AssignmentExpr) e).target, p) || scan(((Ast.AssignmentExpr) e).value, p);
        } else if (e instanceof Ast.SpreadExpr) {
            return scan(((Ast.SpreadExpr) e).target, p);
        } else if (e instanceof Ast.TemplateLiteralExpr) {
            for (Ast.Expression x : ((Ast.TemplateLiteralExpr) e).exprs) if (scan(x, p)) return true;
        } else if (e instanceof Ast.CondExpr) {
            Ast.CondExpr c = (Ast.CondExpr) e;
            return scan(c.cond, p) || scan(c.thenBranch, p) || scan(c.elseBranch, p);
        } else if (e instanceof Ast.ArrowFunctionExpr) {
            Ast.ArrowFunctionExpr a = (Ast.ArrowFunctionExpr) e;
            return scan(a.bodyExpr, p) || scan(a.bodyBlock, p);
        }
        return false;
    }
}
