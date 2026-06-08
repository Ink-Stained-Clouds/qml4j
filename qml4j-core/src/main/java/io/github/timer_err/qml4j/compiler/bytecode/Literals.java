package io.github.timer_err.qml4j.compiler.bytecode;

import io.github.timer_err.qml4j.parser.ast.Ast;

import java.util.regex.Pattern;

// Recognizes a property value whose raw source is exactly one JS literal, so the
// compiler can assign it directly instead of wrapping a RhinoBinding. Mirrors the
// set the parser used to special-case (int / float / string / bool / null /
// undefined); a value with any operator (including a leading '-') is not a literal
// and stays a binding, matching the old AST behaviour.
public final class Literals {

    private Literals() {}

    private static final Pattern INT = Pattern.compile("[0-9]+");
    private static final Pattern FLOAT = Pattern.compile("[0-9]+\\.[0-9]*|\\.[0-9]+");
    private static final Pattern STRING =
        Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'", Pattern.DOTALL);

    public static Ast.LiteralExpr parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        switch (s) {
            case "true": return new Ast.LiteralExpr(Ast.LiteralKind.BOOL, Boolean.TRUE);
            case "false": return new Ast.LiteralExpr(Ast.LiteralKind.BOOL, Boolean.FALSE);
            case "null": return new Ast.LiteralExpr(Ast.LiteralKind.NULL, null);
            case "undefined": return new Ast.LiteralExpr(Ast.LiteralKind.UNDEFINED, null);
            default: break;
        }
        if (INT.matcher(s).matches()) return new Ast.LiteralExpr(Ast.LiteralKind.INT, Long.parseLong(s));
        if (FLOAT.matcher(s).matches()) return new Ast.LiteralExpr(Ast.LiteralKind.FLOAT, Double.parseDouble(s));
        if (STRING.matcher(s).matches()) return new Ast.LiteralExpr(Ast.LiteralKind.STRING, unquote(s));
        return null;
    }

    private static String unquote(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() - 2);
        for (int i = 1; i < raw.length() - 1; i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() - 1) {
                char n = raw.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case '\'': sb.append('\''); break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
