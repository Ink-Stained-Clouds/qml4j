package io.qml4j.compiler;

import io.qml4j.engine.JvmClassLoaderBackend;
import io.qml4j.engine.Property;
import io.qml4j.engine.QObject;
import io.qml4j.parser.Qml4j;
import io.qml4j.parser.ast.Ast;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QmlCompilerTest {

    public static class TestItem extends QObject {
        public final Property<Number> width = new Property<>(0);
        public final Property<Number> height = new Property<>(0);
        public final Property<String> name = new Property<>("");
        public final Property<Boolean> visible = new Property<>(Boolean.FALSE);
        public final Property<Object> ref = new Property<>("initial");
        public final Property<TestItem> child = new Property<>(null);
        public final Property<TestItem> parent = new Property<>(null);
        public final java.util.List<TestItem> children = new java.util.ArrayList<>();
    }

    private static final TypeRegistry REGISTRY = new TypeRegistry()
        .register("TestItem", TestItem.class);

    private static final QmlCompiler COMPILER = new QmlCompiler();
    private static final JvmClassLoaderBackend BACKEND = new JvmClassLoaderBackend();

    private static TestItem instantiate(String qml) throws Exception {
        Ast.QmlDocument doc = Qml4j.parse(qml);
        CompiledUnit unit = COMPILER.compile(doc, REGISTRY);
        Class<?> root = null;
        for (java.util.Map.Entry<String, byte[]> e : unit.classes().entrySet()) {
            Class<?> c = BACKEND.defineClass(e.getKey(), e.getValue());
            if (e.getKey().equals(unit.rootClassName())) root = c;
        }
        Object obj = root.getDeclaredConstructor().newInstance();
        assertTrue(obj instanceof TestItem);
        return (TestItem) obj;
    }

    @Test
    void rootClassNameAndBytes() {
        Ast.QmlDocument doc = Qml4j.parse("TestItem {}");
        CompiledUnit unit = COMPILER.compile(doc, REGISTRY);
        assertTrue(unit.rootClassName().startsWith("io.qml4j.generated.Component$"));
        assertNotNull(unit.rootBytes());
        assertEquals(1, unit.classes().size());
    }

    @Test
    void emptyObjectInstantiates() throws Exception {
        TestItem it = instantiate("TestItem {}");
        assertEquals(0, it.width.peek().intValue());
    }

    @Test
    void intLiteralAssignment() throws Exception {
        TestItem it = instantiate("TestItem { width: 100 }");
        assertEquals(100L, it.width.peek().longValue());
    }

    @Test
    void floatLiteralAssignment() throws Exception {
        TestItem it = instantiate("TestItem { width: 3.14 }");
        assertEquals(3.14, it.width.peek().doubleValue(), 1e-9);
    }

    @Test
    void stringLiteralAssignment() throws Exception {
        TestItem it = instantiate("TestItem { name: \"hello\" }");
        assertEquals("hello", it.name.peek());
    }

    @Test
    void boolLiteralAssignment() throws Exception {
        TestItem it = instantiate("TestItem { visible: true }");
        assertEquals(Boolean.TRUE, it.visible.peek());
    }

    @Test
    void nullLiteralAssignment() throws Exception {
        TestItem it = instantiate("TestItem { ref: null }");
        assertNull(it.ref.peek());
    }

    @Test
    void undefinedLiteralAssignment() throws Exception {
        TestItem it = instantiate("TestItem { ref: undefined }");
        assertNull(it.ref.peek());
    }

    @Test
    void multiplePropertyAssignments() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: 200\n" +
            "  height: 150\n" +
            "  name: \"box\"\n" +
            "  visible: false\n" +
            "}");
        assertEquals(200L, it.width.peek().longValue());
        assertEquals(150L, it.height.peek().longValue());
        assertEquals("box", it.name.peek());
        assertEquals(Boolean.FALSE, it.visible.peek());
    }

    @Test
    void idBindingSilentlyIgnored() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  id: root\n" +
            "  width: 42\n" +
            "}");
        assertEquals(42L, it.width.peek().longValue());
    }

    @Test
    void unknownPropertyRejected() {
        Ast.QmlDocument doc = Qml4j.parse("TestItem { missing: 1 }");
        assertThrows(IllegalArgumentException.class,
            () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void unknownTypeRejected() {
        Ast.QmlDocument doc = Qml4j.parse("Unknown {}");
        assertThrows(IllegalArgumentException.class,
            () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void identifierBindingTracksDep() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: 10\n" +
            "  height: width\n" +
            "}");
        assertEquals(10L, it.height.peek().longValue());
        it.width.set(99);
        assertEquals(99L, it.height.peek().longValue());
    }

    @Test
    void arithmeticBinding() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: 10\n" +
            "  height: width * 2 + 5\n" +
            "}");
        assertEquals(25L, it.height.peek().longValue());
        it.width.set(100);
        assertEquals(205L, it.height.peek().longValue());
    }

    @Test
    void unaryNegation() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: 7\n" +
            "  height: -width\n" +
            "}");
        assertEquals(-7L, it.height.peek().longValue());
    }

    @Test
    void stringConcatenation() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  name: \"hi\"\n" +
            "  ref: name + \"!\"\n" +
            "}");
        assertEquals("hi!", it.ref.peek());
        it.name.set("hello");
        assertEquals("hello!", it.ref.peek());
    }

    @Test
    void conditionalBinding() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: 5\n" +
            "  height: width > 10 ? width * 2 : width + 1\n" +
            "}");
        assertEquals(6L, it.height.peek().longValue());
        it.width.set(20);
        assertEquals(40L, it.height.peek().longValue());
    }

    @Test
    void comparisonAndLogical() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: 5\n" +
            "  visible: width >= 0 && width < 100\n" +
            "}");
        assertEquals(Boolean.TRUE, it.visible.peek());
        it.width.set(150);
        assertEquals(Boolean.FALSE, it.visible.peek());
    }

    @Test
    void memberAccessThroughProperty() throws Exception {
        TestItem inner = new TestItem();
        inner.width.set(50);
        TestItem it = instantiate(
            "TestItem {\n" +
            "  height: child.width + 1\n" +
            "}");
        it.child.set(inner);
        assertEquals(51L, it.height.peek().longValue());
        inner.width.set(200);
        assertEquals(201L, it.height.peek().longValue());

        TestItem inner2 = new TestItem();
        inner2.width.set(7);
        it.child.set(inner2);
        assertEquals(8L, it.height.peek().longValue());
        // changing OLD inner no longer affects
        inner.width.set(999);
        assertEquals(8L, it.height.peek().longValue());
    }

    @Test
    void equalityOperators() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: 5\n" +
            "  visible: width == 5\n" +
            "}");
        assertEquals(Boolean.TRUE, it.visible.peek());
        it.width.set(6);
        assertEquals(Boolean.FALSE, it.visible.peek());
    }

    @Test
    void unknownIdentifierRejected() {
        Ast.QmlDocument doc = Qml4j.parse("TestItem { width: nope + 1 }");
        assertThrows(IllegalArgumentException.class,
            () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void callExpressionRejected() {
        Ast.QmlDocument doc = Qml4j.parse("TestItem { width: foo(1) }");
        assertThrows(UnsupportedOperationException.class,
            () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void groupedPathRejected() {
        Ast.QmlDocument doc = Qml4j.parse("TestItem { anchors.left: 10 }");
        assertThrows(UnsupportedOperationException.class,
            () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void nestedChildAttachesToParent() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  width: 100\n" +
            "  TestItem { width: 50 }\n" +
            "}");
        assertEquals(100L, root.width.peek().longValue());
        assertEquals(1, root.children.size());
        TestItem child = root.children.get(0);
        assertEquals(50L, child.width.peek().longValue());
        assertTrue(child.parent.peek() == root);
    }

    @Test
    void nestedChildBindingUsesParent() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  width: 200\n" +
            "  TestItem { width: parent.width / 2 }\n" +
            "}");
        TestItem child = root.children.get(0);
        assertEquals(100L, child.width.peek().longValue());
        root.width.set(400);
        assertEquals(200L, child.width.peek().longValue());
    }

    @Test
    void multipleSiblingChildren() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  TestItem { width: 1 }\n" +
            "  TestItem { width: 2 }\n" +
            "  TestItem { width: 3 }\n" +
            "}");
        assertEquals(3, root.children.size());
        assertEquals(1L, root.children.get(0).width.peek().longValue());
        assertEquals(2L, root.children.get(1).width.peek().longValue());
        assertEquals(3L, root.children.get(2).width.peek().longValue());
    }

    @Test
    void deeplyNested() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  width: 100\n" +
            "  TestItem {\n" +
            "    TestItem { width: 7 }\n" +
            "  }\n" +
            "}");
        TestItem mid = root.children.get(0);
        TestItem leaf = mid.children.get(0);
        assertEquals(7L, leaf.width.peek().longValue());
        assertTrue(leaf.parent.peek() == mid);
        assertTrue(mid.parent.peek() == root);
    }

    @Test
    void eachCompileProducesUniqueClassName() {
        Ast.QmlDocument doc1 = Qml4j.parse("TestItem {}");
        Ast.QmlDocument doc2 = Qml4j.parse("TestItem {}");
        CompiledUnit u1 = COMPILER.compile(doc1, REGISTRY);
        CompiledUnit u2 = COMPILER.compile(doc2, REGISTRY);
        assertTrue(!u1.rootClassName().equals(u2.rootClassName()));
    }
}
