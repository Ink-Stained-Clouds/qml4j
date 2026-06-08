package io.github.timer_err.qml4j.compiler.bytecode.rhino;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Name;

import java.util.ArrayList;
import java.util.List;

// Recognizes Qt's typed-handler arrow form `(a, b) => body`: the params bind to the
// signal arguments, the body becomes the handler body. Parsed from the raw value
// source with Rhino (the grammar no longer builds a JS AST). Returns null when the
// value is not a single arrow with plain-identifier params, in which case the caller
// treats the whole value as an ordinary handler body.
public final class RhinoArrow {

    private RhinoArrow() {}

    public static final class Result {
        public final List<String> params;
        public final String bodySource;
        Result(List<String> params, String bodySource) {
            this.params = params;
            this.bodySource = bodySource;
        }
    }

    public static Result parse(String source) {
        if (source == null) return null;
        AstRoot root;
        try {
            CompilerEnvirons env = new CompilerEnvirons();
            env.setLanguageVersion(Context.VERSION_ES6);
            env.setRecordingComments(false);
            root = new Parser(env).parse(source, "qml-arrow", 1);
        } catch (RhinoException e) {
            return null;
        }
        if (!(root.getFirstChild() instanceof ExpressionStatement)) return null;
        AstNode expr = ((ExpressionStatement) root.getFirstChild()).getExpression();
        if (!(expr instanceof FunctionNode)) return null;
        FunctionNode fn = (FunctionNode) expr;
        if (fn.getFunctionType() != FunctionNode.ARROW_FUNCTION) return null;

        List<String> params = new ArrayList<>();
        for (AstNode p : fn.getParams()) {
            if (!(p instanceof Name)) return null; // destructured/default params: not handled
            params.add(((Name) p).getIdentifier());
        }
        AstNode body = fn.getBody();
        int pos = body.getAbsolutePosition();
        return new Result(params, source.substring(pos, pos + body.getLength()));
    }
}
