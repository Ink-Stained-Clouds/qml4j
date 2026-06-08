package io.github.timer_err.qml4j.compiler.bytecode.rhino;

import io.github.timer_err.qml4j.engine.js.JsConstRepair;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ast.ArrayLiteral;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.CatchClause;
import org.mozilla.javascript.ast.ConditionalExpression;
import org.mozilla.javascript.ast.ElementGet;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.ForInLoop;
import org.mozilla.javascript.ast.ForLoop;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.IfStatement;
import org.mozilla.javascript.ast.InfixExpression;
import org.mozilla.javascript.ast.LabeledStatement;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NewExpression;
import org.mozilla.javascript.ast.ObjectLiteral;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.ParenthesizedExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.ReturnStatement;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.Spread;
import org.mozilla.javascript.ast.SwitchCase;
import org.mozilla.javascript.ast.SwitchStatement;
import org.mozilla.javascript.ast.TemplateLiteral;
import org.mozilla.javascript.ast.ThrowStatement;
import org.mozilla.javascript.ast.TryStatement;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;
import org.mozilla.javascript.ast.WhileLoop;
import org.mozilla.javascript.ast.DoLoop;

import org.mozilla.javascript.Node;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

// Free identifiers of a JS body/expression, computed from Rhino's own parse of the
// raw source rather than from a hand-rolled QML AST. The grammar no longer defines a
// JS subset, so this is the single source of truth for "which bare names a binding
// reads or writes that it does not itself bind". Contract:
// member/index property names and object-literal keys are not collected;
// var/let/const declarations, function names + params, loop and catch variables bind.
// var and function declarations hoist within their function scope, so a use before a
// later declaration is bound (not free).
public final class RhinoFreeVars {

    private RhinoFreeVars() {}

    public static Set<String> collect(String source, Set<String> initiallyBound) {
        FunctionNode body = parseAsFunctionBody(source);
        Set<String> free = new LinkedHashSet<>();
        Set<String> bound = new HashSet<>(initiallyBound);
        scope(body.getBody(), bound, free);
        return free;
    }

    // Whether the body calls `Qt.binding(arg)` with a non-function first argument.
    // Only the arrow/function form preserves laziness on Rhino; the bare-expression
    // form is rejected by the compiler.
    public static boolean usesBareQtBinding(String source) {
        return scanBareQtBinding(parseAsFunctionBody(source).getBody());
    }

    private static boolean scanBareQtBinding(AstNode n) {
        if (n == null) return false;
        if (n instanceof FunctionCall && !(n instanceof NewExpression)) {
            FunctionCall c = (FunctionCall) n;
            if (isQtBinding(c.getTarget())
                && (c.getArguments().isEmpty() || !(c.getArguments().get(0) instanceof FunctionNode))) {
                return true;
            }
        }
        for (Node c : n) if (scanBareQtBinding((AstNode) c)) return true;
        return false;
    }

    private static boolean isQtBinding(AstNode target) {
        if (!(target instanceof PropertyGet)) return false;
        PropertyGet pg = (PropertyGet) target;
        return pg.getTarget() instanceof Name
            && "Qt".equals(((Name) pg.getTarget()).getIdentifier())
            && "binding".equals(pg.getProperty().getIdentifier());
    }

    // Parse the body wrapped in a function: bodies run under a function at runtime
    // (an expression binding, or `{ ... }` wrapped in an IIFE), so a top-level
    // `return`/`var` is legal and var/function hoisting matches runtime scoping.
    private static FunctionNode parseAsFunctionBody(String source) {
        String wrapped = JsConstRepair.toLet("(function(){\n" + source + "\n})");
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        env.setRecordingComments(false);
        env.setIdeMode(false);
        AstRoot root;
        try {
            root = new Parser(env).parse(wrapped, "qml-binding", 1);
        } catch (RhinoException e) {
            throw new IllegalArgumentException("invalid JS: " + e.getMessage(), e);
        }
        ExpressionStatement st = (ExpressionStatement) root.getFirstChild();
        ParenthesizedExpression pe = (ParenthesizedExpression) st.getExpression();
        return (FunctionNode) pe.getExpression();
    }

    // Walk a function/script/block scope: hoist its declarations into `bound` first
    // (so use-before-declaration is bound), then walk each statement.
    private static void scope(AstNode body, Set<String> bound, Set<String> free) {
        hoist(body, bound);
        for (Node c : body) walk((AstNode) c, bound, free);
    }

    // Hoist var + named-function declarations of this scope (not descending into
    // nested functions, which open their own scope).
    private static void hoist(AstNode body, Set<String> bound) {
        for (Node c : body) {
            AstNode n = (AstNode) c;
            if (n instanceof FunctionNode) {
                Name name = ((FunctionNode) n).getFunctionName();
                if (name != null) bound.add(name.getIdentifier());
            } else if (n instanceof VariableDeclaration) {
                for (VariableInitializer vi : ((VariableDeclaration) n).getVariables()) {
                    bindTarget(vi.getTarget(), bound);
                }
            }
        }
    }

    private static void walk(AstNode n, Set<String> bound, Set<String> free) {
        if (n == null) return;
        if (n instanceof Name) {
            String id = ((Name) n).getIdentifier();
            if (!bound.contains(id)) free.add(id);
        } else if (n instanceof PropertyGet) {
            walk(((PropertyGet) n).getTarget(), bound, free); // skip .property name
        } else if (n instanceof ElementGet) {
            walk(((ElementGet) n).getTarget(), bound, free);
            walk(((ElementGet) n).getElement(), bound, free);
        } else if (n instanceof NewExpression) {
            FunctionCall c = (FunctionCall) n;
            walk(c.getTarget(), bound, free);
            for (AstNode a : c.getArguments()) walk(a, bound, free);
            walk(((NewExpression) n).getInitializer(), bound, free);
        } else if (n instanceof FunctionCall) {
            FunctionCall c = (FunctionCall) n;
            walk(c.getTarget(), bound, free);
            for (AstNode a : c.getArguments()) walk(a, bound, free);
        } else if (n instanceof FunctionNode) {
            walkFunction((FunctionNode) n, bound, free);
        } else if (n instanceof VariableDeclaration) {
            for (VariableInitializer vi : ((VariableDeclaration) n).getVariables()) {
                walk(vi.getInitializer(), bound, free);
                bindTarget(vi.getTarget(), bound);
            }
        } else if (n instanceof InfixExpression) { // binary, logical, assignment, comma
            walk(((InfixExpression) n).getLeft(), bound, free);
            walk(((InfixExpression) n).getRight(), bound, free);
        } else if (n instanceof UnaryExpression) {
            walk(((UnaryExpression) n).getOperand(), bound, free);
        } else if (n instanceof ConditionalExpression) {
            ConditionalExpression c = (ConditionalExpression) n;
            walk(c.getTestExpression(), bound, free);
            walk(c.getTrueExpression(), bound, free);
            walk(c.getFalseExpression(), bound, free);
        } else if (n instanceof ParenthesizedExpression) {
            walk(((ParenthesizedExpression) n).getExpression(), bound, free);
        } else if (n instanceof ArrayLiteral) {
            for (AstNode e : ((ArrayLiteral) n).getElements()) walk(e, bound, free);
        } else if (n instanceof ObjectLiteral) {
            for (org.mozilla.javascript.ast.AbstractObjectProperty p : ((ObjectLiteral) n).getElements()) {
                if (p instanceof ObjectProperty) walk(((ObjectProperty) p).getValue(), bound, free);
                else walk(p, bound, free); // spread property
            }
        } else if (n instanceof Spread) {
            walk(((Spread) n).getExpression(), bound, free);
        } else if (n instanceof TemplateLiteral) {
            for (AstNode e : ((TemplateLiteral) n).getSubstitutions()) walk(e, bound, free);
        } else if (n instanceof ExpressionStatement) {
            walk(((ExpressionStatement) n).getExpression(), bound, free);
        } else if (n instanceof ReturnStatement) {
            walk(((ReturnStatement) n).getReturnValue(), bound, free);
        } else if (n instanceof IfStatement) {
            IfStatement i = (IfStatement) n;
            walk(i.getCondition(), bound, free);
            walk(i.getThenPart(), bound, free);
            walk(i.getElsePart(), bound, free);
        } else if (n instanceof ForLoop) {
            ForLoop f = (ForLoop) n;
            Set<String> inner = new HashSet<>(bound);
            walk(f.getInitializer(), inner, free); // a var decl here binds into inner
            walk(f.getCondition(), inner, free);
            walk(f.getIncrement(), inner, free);
            walk(f.getBody(), inner, free);
        } else if (n instanceof ForInLoop) {
            ForInLoop f = (ForInLoop) n;
            Set<String> inner = new HashSet<>(bound);
            AstNode it = f.getIterator();
            if (it instanceof VariableDeclaration) {
                for (VariableInitializer vi : ((VariableDeclaration) it).getVariables()) {
                    bindTarget(vi.getTarget(), inner);
                }
            } else {
                bindTarget(it, inner);
            }
            walk(f.getIteratedObject(), inner, free);
            walk(f.getBody(), inner, free);
        } else if (n instanceof WhileLoop) {
            walk(((WhileLoop) n).getCondition(), bound, free);
            walk(((WhileLoop) n).getBody(), bound, free);
        } else if (n instanceof DoLoop) {
            walk(((DoLoop) n).getCondition(), bound, free);
            walk(((DoLoop) n).getBody(), bound, free);
        } else if (n instanceof SwitchStatement) {
            walk(((SwitchStatement) n).getExpression(), bound, free);
            for (SwitchCase cl : ((SwitchStatement) n).getCases()) {
                walk(cl.getExpression(), bound, free);
                if (cl.getStatements() != null) {
                    for (AstNode s : cl.getStatements()) walk(s, bound, free);
                }
            }
        } else if (n instanceof TryStatement) {
            TryStatement t = (TryStatement) n;
            walk(t.getTryBlock(), bound, free);
            for (CatchClause cc : t.getCatchClauses()) walk(cc, bound, free);
            walk(t.getFinallyBlock(), bound, free);
        } else if (n instanceof CatchClause) {
            CatchClause cc = (CatchClause) n;
            Set<String> inner = new HashSet<>(bound);
            bindTarget(cc.getVarName(), inner);
            walk(cc.getCatchCondition(), inner, free);
            walk(cc.getBody(), inner, free);
        } else if (n instanceof LabeledStatement) {
            walk(((LabeledStatement) n).getStatement(), bound, free);
        } else if (n instanceof ThrowStatement) {
            walk(((ThrowStatement) n).getExpression(), bound, free);
        } else if (n instanceof Scope) {
            // A bare block (or other block scope): its var/function decls hoist into
            // the same function scope, so reuse the current bound set.
            scope(n, bound, free);
        } else {
            // Literals (number/string/keyword/regexp) and anything else with no binding
            // semantics: descend into children as plain expressions.
            for (Node c : n) walk((AstNode) c, bound, free);
        }
    }

    private static void walkFunction(FunctionNode fn, Set<String> bound, Set<String> free) {
        Set<String> inner = new HashSet<>(bound);
        Name name = fn.getFunctionName();
        if (name != null) inner.add(name.getIdentifier());
        for (AstNode p : fn.getParams()) bindTarget(p, inner);
        AstNode body = fn.getBody();
        if (fn.isExpressionClosure()) {
            walk(body, inner, free); // arrow with expression body
        } else {
            scope(body, inner, free);
        }
    }

    // Bind the names introduced by a declaration/param target: a plain Name, or the
    // Names inside an array/object destructuring pattern (and default-valued params).
    private static void bindTarget(AstNode target, Set<String> bound) {
        if (target == null) return;
        if (target instanceof Name) {
            bound.add(((Name) target).getIdentifier());
        } else if (target instanceof ArrayLiteral) {
            for (AstNode e : ((ArrayLiteral) target).getElements()) bindTarget(e, bound);
        } else if (target instanceof ObjectLiteral) {
            for (org.mozilla.javascript.ast.AbstractObjectProperty p : ((ObjectLiteral) target).getElements()) {
                if (p instanceof ObjectProperty) bindTarget(((ObjectProperty) p).getValue(), bound);
            }
        } else if (target instanceof InfixExpression) {
            // default value `a = expr` in a param/pattern: the left is the binding.
            bindTarget(((InfixExpression) target).getLeft(), bound);
        }
    }
}
