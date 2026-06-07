package io.qml4j.engine.js;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ErrorCollector;
import org.mozilla.javascript.ast.VariableDeclaration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Rewrites every `const` declaration to `let` to work around Rhino 1.9.1, which scopes
// `const` to the enclosing function/script (Parser.defineSymbol -> currentScriptOrFn)
// instead of the block, the way `let` is scoped. Two consequences, both fixed by the
// rewrite:
//   1. Two sibling blocks each declaring `const v` -- legal ES6 -- are rejected as a
//      redeclaration at parse time.
//   2. Worse and silent: a `const` declared in a loop body binds once at function scope,
//      so re-executing the declaration cannot rebind it -- every iteration keeps the
//      FIRST value (`for (i) { const v = data[i]; }` leaves v === data[0] throughout).
// `let` restores correct block scoping. QML binding/handler bodies never rely on const's
// reassignment-throws semantics, so the rewrite is behaviorally transparent for valid
// code, and it is AST-driven so `const` inside strings/comments/identifiers is untouched.
public final class JsConstRepair {

    private JsConstRepair() {}

    // Returns `source` with every `const` declaration keyword rewritten to `let`, or the
    // input unchanged when there is no `const` to rewrite (or the source cannot be parsed
    // even in error-recovery mode -- then the caller's own compile surfaces the error).
    public static String toLet(String source) {
        if (source == null || !source.contains("const")) return source;
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        env.setRecoverFromErrors(true);
        env.setIdeMode(true);
        AstRoot root;
        try {
            root = new Parser(env, new ErrorCollector()).parse(source, "qml-const-rewrite", 1);
        } catch (RhinoException e) {
            return source;
        }
        List<Integer> positions = new ArrayList<>();
        root.visitAll(node -> {
            if (node instanceof VariableDeclaration && node.getType() == Token.CONST) {
                positions.add(node.getAbsolutePosition());
            }
            return true;
        });
        if (positions.isEmpty()) return source;
        // Splice right-to-left so earlier offsets stay valid as "const" (5) -> "let" (3).
        positions.sort(Comparator.reverseOrder());
        StringBuilder sb = new StringBuilder(source);
        for (int p : positions) sb.replace(p, p + "const".length(), "let");
        return sb.toString();
    }
}
