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

    public static final class ChildObject extends ObjectMember {
        public final ObjectNode object;
        public ChildObject(ObjectNode object) { this.object = object; }
        @Override public String toString() { return object.toString(); }
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
