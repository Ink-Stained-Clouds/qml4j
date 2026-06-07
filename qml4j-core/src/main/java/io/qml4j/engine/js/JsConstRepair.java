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

// Works around a Rhino 1.9.1 parser bug: `const` is registered on the enclosing
// function/script scope (Parser.defineSymbol -> currentScriptOrFn.putSymbol) instead
// of the current block scope, the way `let` is. So two sibling blocks each declaring
// `const v` -- legal ES6, distinct block scopes -- are rejected as a redeclaration.
// `let` with the identical structure parses fine. Since QML binding/handler bodies
// never rely on const's reassignment-throws semantics, rewriting the offending
// `const` keywords to `let` is behaviorally equivalent and restores block scoping.
//
// Repair is a fallback invoked only when a parse/compile has already failed: the
// happy path pays nothing, and the trigger is the failure itself (not a localized
// error message), so it is locale-independent.
public final class JsConstRepair {

    private JsConstRepair() {}

    // Returns `source` with every `const` declaration keyword rewritten to `let`, or
    // null when there is nothing to repair (no const decls, or the source has a real
    // syntax error that survives error-recovery parsing) -- in which case the caller
    // should surface its original failure.
    public static String repair(String source) {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        env.setRecoverFromErrors(true);
        env.setIdeMode(true);
        AstRoot root;
        try {
            root = new Parser(env, new ErrorCollector()).parse(source, "qml-const-repair", 1);
        } catch (RhinoException e) {
            return null;
        }
        List<Integer> positions = new ArrayList<>();
        root.visitAll(node -> {
            if (node instanceof VariableDeclaration && node.getType() == Token.CONST) {
                positions.add(node.getAbsolutePosition());
            }
            return true;
        });
        if (positions.isEmpty()) return null;
        // Splice right-to-left so earlier offsets stay valid as "const" (5) -> "let" (3).
        positions.sort(Comparator.reverseOrder());
        StringBuilder sb = new StringBuilder(source);
        for (int p : positions) sb.replace(p, p + "const".length(), "let");
        return sb.toString();
    }
}
