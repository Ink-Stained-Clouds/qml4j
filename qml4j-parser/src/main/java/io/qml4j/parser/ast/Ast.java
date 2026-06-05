package io.qml4j.parser.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Ast {
    private Ast() {}

    public static final class QmlDocument {
        public final List<ImportNode> imports;
        public final List<String> pragmas;
        public final ObjectNode root;
        public QmlDocument(List<ImportNode> imports, ObjectNode root) {
            this(imports, Collections.<String>emptyList(), root);
        }
        public QmlDocument(List<ImportNode> imports, List<String> pragmas, ObjectNode root) {
            this.imports = Collections.unmodifiableList(imports);
            this.pragmas = new ArrayList<>(pragmas);
            this.root = root;
        }
        public boolean hasPragma(String name) {
            for (String p : pragmas) if (p.equals(name)) return true;
            return false;
        }
        public void addPragma(String name) {
            if (!hasPragma(name)) pragmas.add(name);
        }
        @Override public String toString() {
            return "QmlDocument{imports=" + imports
                + (pragmas.isEmpty() ? "" : ", pragmas=" + pragmas)
                + ", root=" + root + "}";
        }
    }

    public static final class ImportNode {
        public final boolean isStringPath;
        public final String moduleOrPath;
        public final String version;
        public final String alias;
        public ImportNode(boolean isStringPath, String moduleOrPath, String version, String alias) {
            this.isStringPath = isStringPath;
            this.moduleOrPath = moduleOrPath;
            this.version = version;
            this.alias = alias;
        }
        @Override public String toString() {
            return "Import{" + (isStringPath ? "\"" + moduleOrPath + "\"" : moduleOrPath)
                + (version != null ? " " + version : "")
                + (alias != null ? " as " + alias : "") + "}";
        }
    }

    public static final class ObjectNode {
        public final String typeName;
        public final List<ObjectMember> members;
        public ObjectNode(String typeName, List<ObjectMember> members) {
            this.typeName = typeName;
            this.members = Collections.unmodifiableList(members);
        }
        @Override public String toString() { return typeName + members; }
    }

    public static abstract class ObjectMember {}

    public static final class PropertyDeclaration extends ObjectMember {
        public final boolean isDefault;
        public final boolean isRequired;
        public final boolean isReadonly;
        public final String typeName;
        public final String name;
        public final Value initializer;
        public PropertyDeclaration(boolean isDefault, boolean isRequired, boolean isReadonly,
                                   String typeName, String name, Value initializer) {
            this.isDefault = isDefault;
            this.isRequired = isRequired;
            this.isReadonly = isReadonly;
            this.typeName = typeName;
            this.name = name;
            this.initializer = initializer;
        }
        @Override public String toString() {
            return "property " + typeName + " " + name + (initializer != null ? " = " + initializer : "");
        }
    }

    public static final class PropertyBinding extends ObjectMember {
        public final List<String> path;
        public final Value value;
        public PropertyBinding(List<String> path, Value value) {
            this.path = Collections.unmodifiableList(path);
            this.value = value;
        }
        @Override public String toString() { return String.join(".", path) + ": " + value; }
    }

    public static final class SignalDeclaration extends ObjectMember {
        public final String name;
        public final List<String> paramNames;
        public SignalDeclaration(String name, List<String> paramNames) {
            this.name = name;
            this.paramNames = Collections.unmodifiableList(paramNames);
        }
        @Override public String toString() { return "signal " + name + paramNames; }
    }

    public static final class ChildObject extends ObjectMember {
        public final ObjectNode object;
        public ChildObject(ObjectNode object) { this.object = object; }
        @Override public String toString() { return object.toString(); }
    }

    public static final class FunctionDeclaration extends ObjectMember {
        public final String name;
        public final List<String> paramNames;
        public final Block body;
        // Raw `{ ... }` JS source of the body (fed to the Rhino backend); null if the
        // parser couldn't capture it, in which case the ASM backend handles it.
        public final String source;
        public FunctionDeclaration(String name, List<String> paramNames, Block body) {
            this(name, paramNames, body, null);
        }
        public FunctionDeclaration(String name, List<String> paramNames, Block body, String source) {
            this.name = name;
            this.paramNames = Collections.unmodifiableList(paramNames);
            this.body = body;
            this.source = source;
        }
        @Override public String toString() {
            return "function " + name + paramNames + " " + body;
        }
    }

    public static final class BehaviorMember extends ObjectMember {
        public final String typeName;
        public final String propertyName;
        public final List<ObjectMember> members;
        public BehaviorMember(String typeName, String propertyName, List<ObjectMember> members) {
            this.typeName = typeName;
            this.propertyName = propertyName;
            this.members = Collections.unmodifiableList(members);
        }
        @Override public String toString() {
            return typeName + " on " + propertyName + " " + members;
        }
    }

    public static abstract class Value {}

    public static final class ObjectValue extends Value {
        public final ObjectNode object;
        public ObjectValue(ObjectNode object) { this.object = object; }
        @Override public String toString() { return object.toString(); }
    }

    public static final class ObjectListValue extends Value {
        public final List<ObjectNode> objects;
        public ObjectListValue(List<ObjectNode> objects) {
            this.objects = Collections.unmodifiableList(objects);
        }
        @Override public String toString() { return objects.toString(); }
    }

    public static final class ExpressionValue extends Value {
        public final Expression expr;
        // Raw JS source substring of the expression (fed to the Rhino backend); null
        // if the parser couldn't capture it, in which case the ASM backend handles it.
        public final String source;
        public ExpressionValue(Expression expr) { this(expr, null); }
        public ExpressionValue(Expression expr, String source) { this.expr = expr; this.source = source; }
        @Override public String toString() { return expr.toString(); }
    }

    public static final class StatementBlockValue extends Value {
        public final Block block;
        // Raw JS source of the handler/function body (fed to the Rhino backend); null
        // if the parser couldn't capture it, in which case the ASM backend handles it.
        public final String source;
        public StatementBlockValue(Block block) { this(block, null); }
        public StatementBlockValue(Block block, String source) { this.block = block; this.source = source; }
        @Override public String toString() { return block.toString(); }
    }

    public static abstract class Statement {}

    public static final class Block extends Statement {
        public final List<Statement> statements;
        public Block(List<Statement> statements) {
            this.statements = Collections.unmodifiableList(statements);
        }
        @Override public String toString() { return "{" + statements + "}"; }
    }

    public static final class ExprStmt extends Statement {
        public final Expression expr;
        public ExprStmt(Expression expr) { this.expr = expr; }
        @Override public String toString() { return expr + ";"; }
    }

    public static final class VarDecl extends Statement {
        public final String name;
        public final Expression init;
        public VarDecl(String name, Expression init) {
            this.name = name;
            this.init = init;
        }
        @Override public String toString() {
            return "var " + name + (init != null ? " = " + init : "") + ";";
        }
    }

    public static final class ReturnStmt extends Statement {
        public final Expression value;
        public ReturnStmt(Expression value) { this.value = value; }
        @Override public String toString() {
            return value == null ? "return;" : "return " + value + ";";
        }
    }

    public static final class IfStmt extends Statement {
        public final Expression cond;
        public final Statement thenBranch;
        public final Statement elseBranch;
        public IfStmt(Expression cond, Statement thenBranch, Statement elseBranch) {
            this.cond = cond;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }
        @Override public String toString() {
            return "if (" + cond + ") " + thenBranch
                + (elseBranch != null ? " else " + elseBranch : "");
        }
    }

    public static final class WhileStmt extends Statement {
        public final Expression cond;
        public final Statement body;
        public WhileStmt(Expression cond, Statement body) {
            this.cond = cond;
            this.body = body;
        }
        @Override public String toString() { return "while (" + cond + ") " + body; }
    }

    public static final class ForStmt extends Statement {
        public final Statement init;
        public final Expression cond;
        public final Expression update;
        public final Statement body;
        public ForStmt(Statement init, Expression cond, Expression update, Statement body) {
            this.init = init;
            this.cond = cond;
            this.update = update;
            this.body = body;
        }
        @Override public String toString() {
            return "for (" + init + "; " + cond + "; " + update + ") " + body;
        }
    }

    public static final class ForInStmt extends Statement {
        public final String varName;
        public final Expression iterable;
        public final Statement body;
        public ForInStmt(String varName, Expression iterable, Statement body) {
            this.varName = varName;
            this.iterable = iterable;
            this.body = body;
        }
        @Override public String toString() {
            return "for (var " + varName + " in " + iterable + ") " + body;
        }
    }

    public static final class SwitchStmt extends Statement {
        public final Expression discriminant;
        public final List<SwitchClause> clauses;
        public SwitchStmt(Expression discriminant, List<SwitchClause> clauses) {
            this.discriminant = discriminant;
            this.clauses = Collections.unmodifiableList(clauses);
        }
        @Override public String toString() { return "switch (" + discriminant + ") {...}"; }
    }

    public static final class SwitchClause {
        public final Expression label;   // null = default
        public final List<Statement> body;
        public SwitchClause(Expression label, List<Statement> body) {
            this.label = label;
            this.body = Collections.unmodifiableList(body);
        }
    }

    public static final class BreakStmt extends Statement {
        @Override public String toString() { return "break;"; }
    }

    public static final class ContinueStmt extends Statement {
        @Override public String toString() { return "continue;"; }
    }

    public static abstract class Expression {}

    public enum LiteralKind { INT, FLOAT, STRING, BOOL, NULL, UNDEFINED }

    public static final class LiteralExpr extends Expression {
        public final LiteralKind kind;
        public final Object value;
        public LiteralExpr(LiteralKind kind, Object value) {
            this.kind = kind;
            this.value = value;
        }
        @Override public String toString() {
            if (kind == LiteralKind.STRING) return "\"" + value + "\"";
            return String.valueOf(value);
        }
    }

    public static final class IdentifierExpr extends Expression {
        public final String name;
        public IdentifierExpr(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public static final class MemberExpr extends Expression {
        public final Expression target;
        public final String property;
        public MemberExpr(Expression target, String property) {
            this.target = target;
            this.property = property;
        }
        @Override public String toString() { return target + "." + property; }
    }

    public static final class IndexExpr extends Expression {
        public final Expression target;
        public final Expression index;
        public IndexExpr(Expression target, Expression index) {
            this.target = target;
            this.index = index;
        }
        @Override public String toString() { return target + "[" + index + "]"; }
    }

    public static final class ArrayLitExpr extends Expression {
        public final List<Expression> elements;
        public ArrayLitExpr(List<Expression> elements) {
            this.elements = Collections.unmodifiableList(elements);
        }
        @Override public String toString() { return elements.toString(); }
    }

    public static final class ObjectLitExpr extends Expression {
        public final List<String> keys;
        public final List<Expression> values;
        public ObjectLitExpr(List<String> keys, List<Expression> values) {
            this.keys = Collections.unmodifiableList(keys);
            this.values = Collections.unmodifiableList(values);
        }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(keys.get(i)).append(": ").append(values.get(i));
            }
            return sb.append("}").toString();
        }
    }

    public static final class CallExpr extends Expression {
        public final Expression callee;
        public final List<Expression> args;
        public CallExpr(Expression callee, List<Expression> args) {
            this.callee = callee;
            this.args = Collections.unmodifiableList(args);
        }
        @Override public String toString() { return callee + "(" + args + ")"; }
    }

    public static final class UnaryExpr extends Expression {
        public final String op;
        public final Expression operand;
        public UnaryExpr(String op, Expression operand) {
            this.op = op;
            this.operand = operand;
        }
        @Override public String toString() { return op + operand; }
    }

    public static final class BinaryExpr extends Expression {
        public final String op;
        public final Expression left;
        public final Expression right;
        public BinaryExpr(String op, Expression left, Expression right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }
        @Override public String toString() { return "(" + left + " " + op + " " + right + ")"; }
    }

    public static final class AssignmentExpr extends Expression {
        public final Expression target;
        public final Expression value;
        public AssignmentExpr(Expression target, Expression value) {
            this.target = target;
            this.value = value;
        }
        @Override public String toString() { return "(" + target + " = " + value + ")"; }
    }

    public static final class SpreadExpr extends Expression {
        public final Expression target;
        public SpreadExpr(Expression target) { this.target = target; }
        @Override public String toString() { return "..." + target; }
    }

    public static final class TemplateLiteralExpr extends Expression {
        public final List<String> rawParts;
        public final List<Expression> exprs;
        public TemplateLiteralExpr(List<String> rawParts, List<Expression> exprs) {
            this.rawParts = Collections.unmodifiableList(rawParts);
            this.exprs = Collections.unmodifiableList(exprs);
        }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("`");
            for (int i = 0; i < rawParts.size(); i++) {
                sb.append(rawParts.get(i));
                if (i < exprs.size()) sb.append("${").append(exprs.get(i)).append("}");
            }
            return sb.append("`").toString();
        }
    }

    public static final class ArrowFunctionExpr extends Expression {
        public final List<String> paramNames;
        public final Expression bodyExpr;
        public final Block bodyBlock;
        // Raw JS source of the arrow body (the `{ ... }` block or the bare expression);
        // null if not captured (e.g. arrows synthesized by the compiler).
        public final String source;
        public ArrowFunctionExpr(List<String> paramNames, Expression bodyExpr, Block bodyBlock) {
            this(paramNames, bodyExpr, bodyBlock, null);
        }
        public ArrowFunctionExpr(List<String> paramNames, Expression bodyExpr, Block bodyBlock, String source) {
            this.paramNames = Collections.unmodifiableList(paramNames);
            this.bodyExpr = bodyExpr;
            this.bodyBlock = bodyBlock;
            this.source = source;
        }
        @Override public String toString() {
            return "(" + String.join(",", paramNames) + ") => "
                + (bodyExpr != null ? bodyExpr.toString() : bodyBlock.toString());
        }
    }

    public static final class CondExpr extends Expression {
        public final Expression cond;
        public final Expression thenBranch;
        public final Expression elseBranch;
        public CondExpr(Expression cond, Expression thenBranch, Expression elseBranch) {
            this.cond = cond;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }
        @Override public String toString() { return "(" + cond + " ? " + thenBranch + " : " + elseBranch + ")"; }
    }
}
