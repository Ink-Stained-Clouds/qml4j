package io.qml4j.parser;

import io.qml4j.parser.ast.Ast;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QmlParserTest {

    @Test
    void emptyObject() {
        Ast.QmlDocument doc = Qml4j.parse("Item {}");
        assertTrue(doc.imports.isEmpty());
        assertEquals("Item", doc.root.typeName);
        assertTrue(doc.root.members.isEmpty());
    }

    @Test
    void imports() {
        Ast.QmlDocument doc = Qml4j.parse(
            "import QtQuick 2.15\n" +
            "import QtQuick.Controls 2.15 as C\n" +
            "import \"./local\" as L\n" +
            "Item {}");
        assertEquals(3, doc.imports.size());

        Ast.ImportNode i0 = doc.imports.get(0);
        assertFalse(i0.isStringPath);
        assertEquals("QtQuick", i0.moduleOrPath);
        assertEquals("2.15", i0.version);
        assertNull(i0.alias);

        Ast.ImportNode i1 = doc.imports.get(1);
        assertEquals("QtQuick.Controls", i1.moduleOrPath);
        assertEquals("2.15", i1.version);
        assertEquals("C", i1.alias);

        Ast.ImportNode i2 = doc.imports.get(2);
        assertTrue(i2.isStringPath);
        assertEquals("./local", i2.moduleOrPath);
        assertEquals("L", i2.alias);
    }

    @Test
    void propertyDeclarationsWithModifiers() {
        Ast.QmlDocument doc = Qml4j.parse(
            "Item {\n" +
            "  property int count: 0\n" +
            "  default property var children\n" +
            "  required property string name\n" +
            "  readonly property real pi: 3.14\n" +
            "}");
        java.util.List<Ast.ObjectMember> ms = doc.root.members;
        assertEquals(4, ms.size());

        Ast.PropertyDeclaration p0 = (Ast.PropertyDeclaration) ms.get(0);
        assertEquals("int", p0.typeName);
        assertEquals("count", p0.name);
        assertNotNull(p0.initializer);

        Ast.PropertyDeclaration p1 = (Ast.PropertyDeclaration) ms.get(1);
        assertTrue(p1.isDefault);
        assertEquals("children", p1.name);
        assertNull(p1.initializer);

        Ast.PropertyDeclaration p2 = (Ast.PropertyDeclaration) ms.get(2);
        assertTrue(p2.isRequired);

        Ast.PropertyDeclaration p3 = (Ast.PropertyDeclaration) ms.get(3);
        assertTrue(p3.isReadonly);
        assertEquals("real", p3.typeName);
    }

    @Test
    void propertyBindingsAndNesting() {
        Ast.QmlDocument doc = Qml4j.parse(
            "Rectangle {\n" +
            "  id: root\n" +
            "  width: 100\n" +
            "  height: width * 2\n" +
            "  color: \"red\"\n" +
            "  Text { text: root.label }\n" +
            "}");
        java.util.List<Ast.ObjectMember> ms = doc.root.members;
        assertEquals(5, ms.size());

        Ast.PropertyBinding idB = (Ast.PropertyBinding) ms.get(0);
        assertEquals(java.util.Collections.singletonList("id"), idB.path);

        Ast.PropertyBinding widthB = (Ast.PropertyBinding) ms.get(1);
        Ast.ExpressionValue widthV = (Ast.ExpressionValue) widthB.value;
        Ast.LiteralExpr lit = (Ast.LiteralExpr) widthV.expr;
        assertEquals(Ast.LiteralKind.INT, lit.kind);
        assertEquals(100L, lit.value);

        Ast.PropertyBinding heightB = (Ast.PropertyBinding) ms.get(2);
        Ast.ExpressionValue heightV = (Ast.ExpressionValue) heightB.value;
        Ast.BinaryExpr bin = (Ast.BinaryExpr) heightV.expr;
        assertEquals("*", bin.op);

        Ast.PropertyBinding colorB = (Ast.PropertyBinding) ms.get(3);
        Ast.LiteralExpr colorLit = (Ast.LiteralExpr) ((Ast.ExpressionValue) colorB.value).expr;
        assertEquals(Ast.LiteralKind.STRING, colorLit.kind);
        assertEquals("red", colorLit.value);

        Ast.ChildObject child = (Ast.ChildObject) ms.get(4);
        assertEquals("Text", child.object.typeName);
        Ast.PropertyBinding textB = (Ast.PropertyBinding) child.object.members.get(0);
        Ast.MemberExpr mem = (Ast.MemberExpr) ((Ast.ExpressionValue) textB.value).expr;
        assertEquals("label", mem.property);
        assertInstanceOf(Ast.IdentifierExpr.class, mem.target);
    }

    @Test
    void groupedPropertyPath() {
        Ast.QmlDocument doc = Qml4j.parse(
            "Item { anchors.left: parent.left }");
        Ast.PropertyBinding b = (Ast.PropertyBinding) doc.root.members.get(0);
        assertEquals(java.util.Arrays.asList("anchors", "left"), b.path);
    }

    @Test
    void expressionPrecedenceAndConditional() {
        Ast.QmlDocument doc = Qml4j.parse(
            "Item { value: a + b * c > 10 ? -d : e.f(1, 2) }");
        Ast.PropertyBinding b = (Ast.PropertyBinding) doc.root.members.get(0);
        Ast.CondExpr cond = (Ast.CondExpr) ((Ast.ExpressionValue) b.value).expr;

        Ast.BinaryExpr gt = (Ast.BinaryExpr) cond.cond;
        assertEquals(">", gt.op);
        Ast.BinaryExpr plus = (Ast.BinaryExpr) gt.left;
        assertEquals("+", plus.op);
        Ast.BinaryExpr mul = (Ast.BinaryExpr) plus.right;
        assertEquals("*", mul.op);

        Ast.UnaryExpr neg = (Ast.UnaryExpr) cond.thenBranch;
        assertEquals("-", neg.op);

        Ast.CallExpr call = (Ast.CallExpr) cond.elseBranch;
        assertEquals(2, call.args.size());
        Ast.MemberExpr callee = (Ast.MemberExpr) call.callee;
        assertEquals("f", callee.property);
    }

    @Test
    void objectListValue() {
        Ast.QmlDocument doc = Qml4j.parse(
            "Item { states: [ State {}, State {} ] }");
        Ast.PropertyBinding b = (Ast.PropertyBinding) doc.root.members.get(0);
        Ast.ObjectListValue list = (Ast.ObjectListValue) b.value;
        assertEquals(2, list.objects.size());
        assertEquals("State", list.objects.get(0).typeName);
    }

    @Test
    void objectValue() {
        Ast.QmlDocument doc = Qml4j.parse(
            "Item { state: State {} }");
        Ast.PropertyBinding b = (Ast.PropertyBinding) doc.root.members.get(0);
        Ast.ObjectValue v = (Ast.ObjectValue) b.value;
        assertEquals("State", v.object.typeName);
    }

    @Test
    void literals() {
        Ast.QmlDocument doc = Qml4j.parse(
            "Item {\n" +
            "  a: 3.14\n" +
            "  b: true\n" +
            "  c: false\n" +
            "  d: null\n" +
            "  e: undefined\n" +
            "}");
        java.util.List<Ast.ObjectMember> ms = doc.root.members;
        assertEquals(Ast.LiteralKind.FLOAT, litOf(ms.get(0)).kind);
        assertEquals(3.14, (Double) litOf(ms.get(0)).value, 1e-9);
        assertEquals(Boolean.TRUE, litOf(ms.get(1)).value);
        assertEquals(Boolean.FALSE, litOf(ms.get(2)).value);
        assertEquals(Ast.LiteralKind.NULL, litOf(ms.get(3)).kind);
        assertEquals(Ast.LiteralKind.UNDEFINED, litOf(ms.get(4)).kind);
    }

    @Test
    void syntaxErrorThrowsWithPosition() {
        Qml4j.QmlSyntaxException ex = assertThrows(
            Qml4j.QmlSyntaxException.class,
            () -> Qml4j.parse("Item { width: }"));
        assertTrue(ex.line >= 1);
    }

    private static Ast.LiteralExpr litOf(Ast.ObjectMember m) {
        Ast.PropertyBinding b = (Ast.PropertyBinding) m;
        return (Ast.LiteralExpr) ((Ast.ExpressionValue) b.value).expr;
    }
}
