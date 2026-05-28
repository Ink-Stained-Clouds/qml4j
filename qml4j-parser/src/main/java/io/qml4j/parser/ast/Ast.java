package io.qml4j.parser.ast;

import java.util.Collections;
import java.util.List;

public final class Ast {
    private Ast() {}

    public static final class QmlDocument {
        public final List<ImportNode> imports;
        public final ObjectNode root;
        public QmlDocument(List<ImportNode> imports, ObjectNode root) {
            this.imports = Collections.unmodifiableList(imports);
            this.root = root;
        }
        @Override public String toString() { return "QmlDocument{imports=" + imports + ", root=" + root + "}"; }
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
        public FunctionDeclaration(String name, List<String> paramNames, Block body) {
            this.name = name;
            this.paramNames = Collections.unmodifiableList(paramNames);
            this.body = body;
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
        public ExpressionValue(Expression expr) { this.expr = expr; }
        @Override public String toString() { return expr.toString(); }
    }

    public static final class StatementBlockValue extends Value {
        public final Block block;
        public StatementBlockValue(Block block) { this.block = block; }
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
