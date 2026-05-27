package io.qml4j.compiler;

import io.qml4j.compiler.bytecode.QmlCompiler;
import io.qml4j.engine.classloader.JvmClassLoaderBackend;
import io.qml4j.engine.binding.Property;
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
    void idReferenceResolvesToRoot() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  id: root\n" +
            "  width: 10\n" +
            "  height: root.width * 3\n" +
            "}");
        assertEquals(30L, it.height.peek().longValue());
        it.width.set(7);
        assertEquals(21L, it.height.peek().longValue());
    }

    @Test
    void idReferenceFromChildToAncestor() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  id: outer\n" +
            "  width: 50\n" +
            "  TestItem {\n" +
            "    width: outer.width + 1\n" +
            "  }\n" +
            "}");
        assertEquals(51L, root.children.get(0).width.peek().longValue());
        root.width.set(99);
        assertEquals(100L, root.children.get(0).width.peek().longValue());
    }

    @Test
    void idReferenceCrossSibling() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  TestItem {\n" +
            "    id: a\n" +
            "    width: 4\n" +
            "  }\n" +
            "  TestItem {\n" +
            "    width: a.width + 10\n" +
            "  }\n" +
            "}");
        assertEquals(14L, root.children.get(1).width.peek().longValue());
        root.children.get(0).width.set(20);
        assertEquals(30L, root.children.get(1).width.peek().longValue());
    }

    @Test
    void customSignalEmitNoArgs() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pinged()\n" +
            "  onPinged: ref = \"pong\"\n" +
            "}");
        io.qml4j.engine.Signal sig = (io.qml4j.engine.Signal) it.getClass().getField("pinged").get(it);
        sig.emit();
        assertEquals("pong", it.ref.peek());
    }

    @Test
    void customSignalArgsAccessibleInHandler() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pressed(int x, int y)\n" +
            "  onPressed: ref = x + y\n" +
            "}");
        io.qml4j.engine.Signal sig = (io.qml4j.engine.Signal) it.getClass().getField("pressed").get(it);
        sig.emit(3L, 4L);
        assertEquals(7L, ((Number) it.ref.peek()).longValue());
        sig.emit(100L, 200L);
        assertEquals(300L, ((Number) it.ref.peek()).longValue());
    }

    @Test
    void childObjectCustomSignal() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  TestItem {\n" +
            "    id: c\n" +
            "    signal poked(int v)\n" +
            "    onPoked: r.ref = v\n" +
            "  }\n" +
            "}");
        TestItem child = root.children.get(0);
        io.qml4j.engine.Signal sig =
            (io.qml4j.engine.Signal) child.getClass().getField("poked").get(child);
        sig.emit(42L);
        assertEquals(42L, ((Number) root.ref.peek()).longValue());
    }

    @Test
    void customSignalArgsWithStringPayload() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal said(msg)\n" +
            "  onSaid: name = msg\n" +
            "}");
        io.qml4j.engine.Signal sig = (io.qml4j.engine.Signal) it.getClass().getField("said").get(it);
        sig.emit("hello");
        assertEquals("hello", it.name.peek());
    }

    @Test
    void duplicateIdRejected() {
        Ast.QmlDocument doc = Qml4j.parse(
            "TestItem {\n" +
            "  id: foo\n" +
            "  TestItem { id: foo }\n" +
            "}");
        assertThrows(IllegalArgumentException.class,
            () -> COMPILER.compile(doc, REGISTRY));
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
    void unknownGroupRejected() {
        Ast.QmlDocument doc = Qml4j.parse("TestItem { mystery.left: 10 }");
        assertThrows(IllegalArgumentException.class,
            () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void deepGroupedPathRejected() {
        Ast.QmlDocument doc = Qml4j.parse("TestItem { a.b.c: 10 }");
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
    void handlerCanEmitOwnSignalViaId() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  signal pinged()\n" +
            "  signal poked()\n" +
            "  onPoked: r.pinged.emit()\n" +
            "  onPinged: ref = \"pinged\"\n" +
            "}");
        io.qml4j.engine.Signal poked = (io.qml4j.engine.Signal) root.getClass().getField("poked").get(root);
        poked.emit();
        assertEquals("pinged", root.ref.peek());
    }

    @Test
    void handlerCanEmitSignalWithArgs() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  signal pressed(int x, int y)\n" +
            "  signal trigger()\n" +
            "  onTrigger: r.pressed.emit(7, 9)\n" +
            "  onPressed: ref = x + y\n" +
            "}");
        io.qml4j.engine.Signal trigger = (io.qml4j.engine.Signal) root.getClass().getField("trigger").get(root);
        trigger.emit();
        assertEquals(16L, ((Number) root.ref.peek()).longValue());
    }

    @Test
    void handlerCanEmitChildSignalViaChildId() throws Exception {
        TestItem root = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  signal trigger()\n" +
            "  TestItem {\n" +
            "    id: c\n" +
            "    signal poked(int v)\n" +
            "    onPoked: r.ref = v\n" +
            "  }\n" +
            "  onTrigger: c.poked.emit(100)\n" +
            "}");
        io.qml4j.engine.Signal trigger = (io.qml4j.engine.Signal) root.getClass().getField("trigger").get(root);
        trigger.emit();
        assertEquals(100L, ((Number) root.ref.peek()).longValue());
    }

    @Test
    void handlerStatementBlockMultipleStatements() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pinged()\n" +
            "  onPinged: {\n" +
            "    width = 10;\n" +
            "    height = 20;\n" +
            "    ref = \"done\";\n" +
            "  }\n" +
            "}");
        io.qml4j.engine.Signal sig = (io.qml4j.engine.Signal) it.getClass().getField("pinged").get(it);
        sig.emit();
        assertEquals(10L, it.width.peek().longValue());
        assertEquals(20L, it.height.peek().longValue());
        assertEquals("done", it.ref.peek());
    }

    @Test
    void handlerStatementBlockVarDeclaration() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pinged()\n" +
            "  onPinged: {\n" +
            "    var sum = width + height;\n" +
            "    ref = sum;\n" +
            "  }\n" +
            "}");
        it.width.set(7);
        it.height.set(3);
        io.qml4j.engine.Signal sig = (io.qml4j.engine.Signal) it.getClass().getField("pinged").get(it);
        sig.emit();
        assertEquals(10L, ((Number) it.ref.peek()).longValue());
    }

    @Test
    void handlerStatementBlockIfElse() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pinged()\n" +
            "  onPinged: {\n" +
            "    if (width > 5) {\n" +
            "      ref = \"big\";\n" +
            "    } else {\n" +
            "      ref = \"small\";\n" +
            "    }\n" +
            "  }\n" +
            "}");
        io.qml4j.engine.Signal sig = (io.qml4j.engine.Signal) it.getClass().getField("pinged").get(it);
        it.width.set(10);
        sig.emit();
        assertEquals("big", it.ref.peek());
        it.width.set(2);
        sig.emit();
        assertEquals("small", it.ref.peek());
    }

    @Test
    void handlerStatementBlockIfNoElse() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pinged()\n" +
            "  onPinged: {\n" +
            "    if (width > 5) ref = \"big\";\n" +
            "  }\n" +
            "}");
        io.qml4j.engine.Signal sig = (io.qml4j.engine.Signal) it.getClass().getField("pinged").get(it);
        it.ref.set("untouched");
        it.width.set(2);
        sig.emit();
        assertEquals("untouched", it.ref.peek());
        it.width.set(10);
        sig.emit();
        assertEquals("big", it.ref.peek());
    }

    @Test
    void statementBlockOnNonHandlerRejected() {
        Ast.QmlDocument doc = Qml4j.parse(
            "TestItem {\n" +
            "  width: { var x = 5; x }\n" +
            "}");
        assertThrows(UnsupportedOperationException.class,
            () -> COMPILER.compile(doc, REGISTRY));
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
