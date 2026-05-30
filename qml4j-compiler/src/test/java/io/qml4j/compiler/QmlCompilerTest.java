package io.qml4j.compiler;

import io.qml4j.compiler.bytecode.QmlCompiler;
import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.Signal;
import io.qml4j.engine.classloader.JvmClassLoaderBackend;
import io.qml4j.engine.binding.Property;
import io.qml4j.engine.QObject;
import io.qml4j.parser.Qml4j;
import io.qml4j.parser.ast.Ast;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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

    public static class TestRepeater extends TestItem implements DelegateHost {
        public final Property<Object> model = new Property<>(0);
        private DelegateFactory factory;
        public final List<TestItem> instances = new ArrayList<>();

        public TestRepeater() {
            model.addListener(v -> rebuild());
            parent.addListener(v -> rebuild());
        }

        @Override
        public void setDelegate(DelegateFactory factory) {
            this.factory = factory;
            rebuild();
        }

        private void rebuild() {
            if (factory == null) return;
            TestItem visualParent = parent.peek();
            if (visualParent == null) return;
            Object m = model.peek();
            int desired = sizeOf(m);
            while (instances.size() > desired) {
                TestItem last = instances.remove(instances.size() - 1);
                visualParent.children.remove(last);
            }
            for (int i = instances.size(); i < desired; i++) {
                Object data = dataAt(m, i);
                QObject created = factory.create(i, data);
                TestItem item = (TestItem) created;
                item.parent.set(visualParent);
                visualParent.children.add(item);
                instances.add(item);
            }
        }

        private static int sizeOf(Object m) {
            if (m instanceof Number) {
                int n = ((Number) m).intValue();
                return n < 0 ? 0 : n;
            }
            if (m instanceof List) return ((List<?>) m).size();
            return 0;
        }

        private static Object dataAt(Object m, int i) {
            if (m instanceof List) {
                List<?> list = (List<?>) m;
                return i < list.size() ? list.get(i) : null;
            }
            return i;
        }
    }

    private static final TypeRegistry REGISTRY = new TypeRegistry()
        .register("TestItem", TestItem.class)
        .register("TestRepeater", TestRepeater.class);

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

    private static Property<?> declaredProp(Object obj, String name) throws Exception {
        Field f = obj.getClass().getField(name);
        return (Property<?>) f.get(obj);
    }

    @Test
    void rootDeclaredIntDefault() throws Exception {
        TestItem it = instantiate("TestItem { property int count }");
        assertEquals(0L, ((Number) declaredProp(it, "count").peek()).longValue());
    }

    @Test
    void rootDeclaredRealDefault() throws Exception {
        TestItem it = instantiate("TestItem { property real ratio }");
        assertEquals(0.0, ((Number) declaredProp(it, "ratio").peek()).doubleValue(), 0);
    }

    @Test
    void rootDeclaredStringDefault() throws Exception {
        TestItem it = instantiate("TestItem { property string label }");
        assertEquals("", declaredProp(it, "label").peek());
    }

    @Test
    void rootDeclaredBoolDefault() throws Exception {
        TestItem it = instantiate("TestItem { property bool enabled }");
        assertEquals(Boolean.FALSE, declaredProp(it, "enabled").peek());
    }

    @Test
    void rootDeclaredVarDefault() throws Exception {
        TestItem it = instantiate("TestItem { property var payload }");
        assertNull(declaredProp(it, "payload").peek());
    }

    @Test
    void rootDeclaredLiteralInit() throws Exception {
        TestItem it = instantiate("TestItem { property int count: 42 }");
        assertEquals(42L, ((Number) declaredProp(it, "count").peek()).longValue());
    }

    @Test
    void rootDeclaredBindingFromIntrinsic() throws Exception {
        TestItem it = instantiate(
            "TestItem { width: 10; property int doubled: width * 2 }");
        assertEquals(20L, ((Number) declaredProp(it, "doubled").peek()).longValue());
    }

    @Test
    void rootDeclaredBindingReactsToIntrinsic() throws Exception {
        TestItem it = instantiate(
            "TestItem { width: 5; property int doubled: width * 2 }");
        assertEquals(10L, ((Number) declaredProp(it, "doubled").peek()).longValue());
        it.width.set(7);
        assertEquals(14L, ((Number) declaredProp(it, "doubled").peek()).longValue());
    }

    @Test
    void rootDeclaredReadByIntrinsicBinding() throws Exception {
        TestItem it = instantiate(
            "TestItem { property int base: 4; width: base * 3 }");
        assertEquals(12L, it.width.peek().longValue());
        ((Property<Object>) declaredProp(it, "base")).set(5L);
        assertEquals(15L, it.width.peek().longValue());
    }

    @Test
    void childDeclaredPropertyDefault() throws Exception {
        TestItem it = instantiate(
            "TestItem { TestItem { id: c; property int n } }");
        TestItem c = it.children.get(0);
        assertEquals(0L, ((Number) declaredProp(c, "n").peek()).longValue());
    }

    @Test
    void childDeclaredPropertyInBinding() throws Exception {
        TestItem it = instantiate(
            "TestItem { TestItem { id: c; property int n: 3; width: n * 5 } }");
        TestItem c = it.children.get(0);
        assertEquals(15L, c.width.peek().longValue());
        ((Property<Object>) declaredProp(c, "n")).set(4L);
        assertEquals(20L, c.width.peek().longValue());
    }

    @Test
    void handlerAssignsDeclaredProperty() throws Exception {
        TestItem it = instantiate(
            "TestItem {"
          + "  signal poke()"
          + "  property int count: 0"
          + "  onPoke: count = count + 1"
          + "}");
        Field sigField = it.getClass().getField("poke");
        Signal sig = (Signal) sigField.get(it);
        sig.emit();
        sig.emit();
        assertEquals(2L, ((Number) declaredProp(it, "count").peek()).longValue());
    }

    @Test
    void declaredPropertyShadowingFieldRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            instantiate("TestItem { property int width }"));
    }

    @Test
    void duplicateDeclaredPropertyRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            instantiate("TestItem { property int a; property int a }"));
    }

    @Test
    void aliasToBuiltinProperty() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  width: 42\n" +
            "  property alias w: r.width\n" +
            "}");
        assertEquals(42L, ((Number) declaredProp(it, "w").peek()).longValue());
        Property<?> alias = declaredProp(it, "w");
        assertTrue(alias == it.width);
    }

    @Test
    void aliasWriteMutatesTarget() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  width: 1\n" +
            "  signal poke()\n" +
            "  property alias w: r.width\n" +
            "  onPoke: w = 99\n" +
            "}");
        Signal sig = (Signal) it.getClass().getField("poke").get(it);
        sig.emit();
        assertEquals(99L, it.width.peek().longValue());
    }

    @Test
    void aliasReadInBindingTracksTarget() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  width: 3\n" +
            "  property alias w: r.width\n" +
            "  height: w * 4\n" +
            "}");
        assertEquals(12L, it.height.peek().longValue());
        it.width.set(10);
        assertEquals(40L, it.height.peek().longValue());
    }

    @Test
    void aliasToRootDeclaredProperty() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  property int count: 7\n" +
            "  property alias c: r.count\n" +
            "}");
        assertEquals(7L, ((Number) declaredProp(it, "c").peek()).longValue());
        Property<?> alias = declaredProp(it, "c");
        Property<?> target = declaredProp(it, "count");
        assertTrue(alias == target);
    }

    @Test
    void aliasFromChildIdToTargetProperty() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  property alias cw: c.width\n" +
            "  TestItem { id: c; width: 11 }\n" +
            "}");
        assertEquals(11L, ((Number) declaredProp(it, "cw").peek()).longValue());
        it.children.get(0).width.set(22);
        assertEquals(22L, ((Number) declaredProp(it, "cw").peek()).longValue());
    }

    @Test
    void aliasInitializerMustBeMemberExpr() {
        assertThrows(IllegalArgumentException.class, () ->
            instantiate("TestItem { id: r; property alias w: 42 }"));
    }

    @Test
    void aliasMissingInitializerRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            instantiate("TestItem { id: r; property alias w }"));
    }

    @Test
    void aliasUnknownTargetIdRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            instantiate("TestItem { id: r; property alias w: nope.width }"));
    }

    @Test
    void eachCompileProducesUniqueClassName() {
        Ast.QmlDocument doc1 = Qml4j.parse("TestItem {}");
        Ast.QmlDocument doc2 = Qml4j.parse("TestItem {}");
        CompiledUnit u1 = COMPILER.compile(doc1, REGISTRY);
        CompiledUnit u2 = COMPILER.compile(doc2, REGISTRY);
        assertTrue(!u1.rootClassName().equals(u2.rootClassName()));
    }

    @Test
    void functionCalledFromHandlerNoArgs() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pinged()\n" +
            "  function bump() { width = 42; }\n" +
            "  onPinged: bump()\n" +
            "}");
        Signal sig = (Signal) it.getClass().getField("pinged").get(it);
        sig.emit();
        assertEquals(42L, it.width.peek().longValue());
    }

    @Test
    void functionWithArgsCalledFromHandler() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal pinged()\n" +
            "  function setBoth(a, b) { width = a; height = b; }\n" +
            "  onPinged: setBoth(7, 9)\n" +
            "}");
        Signal sig = (Signal) it.getClass().getField("pinged").get(it);
        sig.emit();
        assertEquals(7L, it.width.peek().longValue());
        assertEquals(9L, it.height.peek().longValue());
    }

    @Test
    void functionReturnUsedInBinding() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function answer() { return 99; }\n" +
            "  width: answer()\n" +
            "}");
        assertEquals(99L, it.width.peek().longValue());
    }

    @Test
    void functionRecursionFib() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function fib(n) {\n" +
            "    if (n < 2) { return n; }\n" +
            "    return fib(n - 1) + fib(n - 2);\n" +
            "  }\n" +
            "  width: fib(10)\n" +
            "}");
        assertEquals(55L, it.width.peek().longValue());
    }

    @Test
    void functionCallsAnotherFunction() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function double(x) { return x * 2; }\n" +
            "  function quad(x) { return double(double(x)); }\n" +
            "  width: quad(3)\n" +
            "}");
        assertEquals(12L, it.width.peek().longValue());
    }

    @Test
    void functionWithoutReturnYieldsNull() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function noop() { width = 5; }\n" +
            "  ref: noop()\n" +
            "}");
        assertEquals(5L, it.width.peek().longValue());
        assertNull(it.ref.peek());
    }

    @Test
    void functionCalledFromChildHandler() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  id: r\n" +
            "  function bump() { r.width = 77; }\n" +
            "  TestItem {\n" +
            "    id: c\n" +
            "    signal poked()\n" +
            "    onPoked: bump()\n" +
            "  }\n" +
            "}");
        Object child = it.children.get(0);
        Signal poked = (Signal) child.getClass().getField("poked").get(child);
        poked.emit();
        assertEquals(77L, it.width.peek().longValue());
    }

    @Test
    void duplicateFunctionRejected() {
        Ast.QmlDocument doc = Qml4j.parse(
            "TestItem {\n" +
            "  function f() { return 1; }\n" +
            "  function f() { return 2; }\n" +
            "}");
        assertThrows(IllegalArgumentException.class, () -> COMPILER.compile(doc, REGISTRY));
    }


    @Test
    void functionShadowingFieldRejected() {
        Ast.QmlDocument doc = Qml4j.parse(
            "TestItem {\n" +
            "  function width() { return 1; }\n" +
            "}");
        assertThrows(IllegalArgumentException.class, () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void whileLoopCountsInHandler() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  signal go()\n" +
            "  onGo: {\n" +
            "    var i = 0;\n" +
            "    while (i < 5) { i = i + 1; }\n" +
            "    width = i;\n" +
            "  }\n" +
            "}");
        Signal sig = (Signal) it.getClass().getField("go").get(it);
        sig.emit();
        assertEquals(5L, it.width.peek().longValue());
    }

    @Test
    void forLoopSumsToProperty() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function sum(n) {\n" +
            "    var s = 0;\n" +
            "    for (var i = 1; i <= n; i = i + 1) { s = s + i; }\n" +
            "    return s;\n" +
            "  }\n" +
            "  width: sum(10)\n" +
            "}");
        assertEquals(55L, it.width.peek().longValue());
    }

    @Test
    void breakExitsLoop() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function firstOver(limit) {\n" +
            "    var i = 0;\n" +
            "    while (true) {\n" +
            "      if (i >= limit) { break; }\n" +
            "      i = i + 1;\n" +
            "    }\n" +
            "    return i;\n" +
            "  }\n" +
            "  width: firstOver(7)\n" +
            "}");
        assertEquals(7L, it.width.peek().longValue());
    }

    @Test
    void continueSkipsIteration() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function sumOdd(n) {\n" +
            "    var s = 0;\n" +
            "    for (var i = 0; i < n; i = i + 1) {\n" +
            "      if (i % 2 == 0) { continue; }\n" +
            "      s = s + i;\n" +
            "    }\n" +
            "    return s;\n" +
            "  }\n" +
            "  width: sumOdd(10)\n" +
            "}");
        assertEquals(25L, it.width.peek().longValue());
    }

    @Test
    void nestedLoopsWithInnerBreak() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function countPairs(n) {\n" +
            "    var c = 0;\n" +
            "    for (var i = 0; i < n; i = i + 1) {\n" +
            "      for (var j = 0; j < n; j = j + 1) {\n" +
            "        if (j >= i) { break; }\n" +
            "        c = c + 1;\n" +
            "      }\n" +
            "    }\n" +
            "    return c;\n" +
            "  }\n" +
            "  width: countPairs(5)\n" +
            "}");
        assertEquals(10L, it.width.peek().longValue());
    }

    @Test
    void forLoopOmittedInitAndUpdate() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function countUp(n) {\n" +
            "    var i = 0;\n" +
            "    for (; i < n; ) { i = i + 1; }\n" +
            "    return i;\n" +
            "  }\n" +
            "  width: countUp(4)\n" +
            "}");
        assertEquals(4L, it.width.peek().longValue());
    }

    @Test
    void forLoopOmittedConditionWithBreak() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function firstHit(limit) {\n" +
            "    var i = 0;\n" +
            "    for (;;) {\n" +
            "      if (i == limit) { break; }\n" +
            "      i = i + 1;\n" +
            "    }\n" +
            "    return i;\n" +
            "  }\n" +
            "  width: firstHit(6)\n" +
            "}");
        assertEquals(6L, it.width.peek().longValue());
    }

    @Test
    void breakOutsideLoopRejected() {
        Ast.QmlDocument doc = Qml4j.parse(
            "TestItem {\n" +
            "  signal go()\n" +
            "  onGo: { break; }\n" +
            "}");
        assertThrows(IllegalArgumentException.class, () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void continueOutsideLoopRejected() {
        Ast.QmlDocument doc = Qml4j.parse(
            "TestItem {\n" +
            "  function f() { continue; }\n" +
            "}");
        assertThrows(IllegalArgumentException.class, () -> COMPILER.compile(doc, REGISTRY));
    }

    @Test
    void arrayLiteralIndexRead() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function pick(i) { var xs = [10, 20, 30]; return xs[i]; }\n" +
            "  width: pick(0) + pick(1) + pick(2)\n" +
            "}");
        assertEquals(60L, it.width.peek().longValue());
    }

    @Test
    void arrayLengthReads() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  width: [1, 2, 3, 4, 5].length\n" +
            "}");
        assertEquals(5L, it.width.peek().longValue());
    }

    @Test
    void arrayIndexWriteAndSum() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function build() {\n" +
            "    var xs = [0, 0, 0];\n" +
            "    var i = 0;\n" +
            "    while (i < 3) { xs[i] = (i + 1) * 7; i = i + 1; }\n" +
            "    var s = 0;\n" +
            "    for (var k = 0; k < xs.length; k = k + 1) { s = s + xs[k]; }\n" +
            "    return s;\n" +
            "  }\n" +
            "  width: build()\n" +
            "}");
        assertEquals(42L, it.width.peek().longValue());
    }

    @Test
    void objectLiteralRead() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function cfg() { return { w: 33, h: 44 }; }\n" +
            "  width: cfg().w\n" +
            "  height: cfg()[\"h\"]\n" +
            "}");
        assertEquals(33L, it.width.peek().longValue());
        assertEquals(44L, it.height.peek().longValue());
    }

    @Test
    void objectLiteralStringKey() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function cfg() { return { \"a-1\": 7, b: 8 }; }\n" +
            "  width: cfg()[\"a-1\"] + cfg().b\n" +
            "}");
        assertEquals(15L, it.width.peek().longValue());
    }

    @Test
    void nestedArrayLiteral() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  function grid() { return [[1, 2], [3, 4]]; }\n" +
            "  width: grid()[1][0] + grid()[1][1]\n" +
            "}");
        assertEquals(7L, it.width.peek().longValue());
    }

    @Test
    void stringIndexAndLength() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  name: \"qml\"[1]\n" +
            "  width: \"hello\".length\n" +
            "}");
        assertEquals("m", it.name.peek());
        assertEquals(5L, it.width.peek().longValue());
    }

    @Test
    void repeaterIntegerModelGrowsChildren() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  TestRepeater {\n" +
            "    id: rep\n" +
            "    model: 3\n" +
            "    TestItem { width: index * 10 }\n" +
            "  }\n" +
            "}");
        TestRepeater rep = (TestRepeater) it.getClass().getField("rep").get(it);
        assertEquals(3, rep.instances.size());
        assertEquals(0L, rep.instances.get(0).width.peek().longValue());
        assertEquals(10L, rep.instances.get(1).width.peek().longValue());
        assertEquals(20L, rep.instances.get(2).width.peek().longValue());
        assertEquals(4, it.children.size());
        assertTrue(it.children.get(0) instanceof TestRepeater);
    }

    @Test
    void repeaterShrinksWhenModelDecreases() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  TestRepeater {\n" +
            "    id: rep\n" +
            "    model: 5\n" +
            "    TestItem { width: index }\n" +
            "  }\n" +
            "}");
        TestRepeater rep = (TestRepeater) it.getClass().getField("rep").get(it);
        assertEquals(5, rep.instances.size());
        rep.model.set(2);
        assertEquals(2, rep.instances.size());
        assertEquals(0L, rep.instances.get(0).width.peek().longValue());
        assertEquals(1L, rep.instances.get(1).width.peek().longValue());
    }

    @Test
    void repeaterListModelBindsModelData() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  TestRepeater {\n" +
            "    id: rep\n" +
            "    model: [\"alpha\", \"beta\", \"gamma\"]\n" +
            "    TestItem { name: modelData }\n" +
            "  }\n" +
            "}");
        TestRepeater rep = (TestRepeater) it.getClass().getField("rep").get(it);
        assertEquals(3, rep.instances.size());
        assertEquals("alpha", rep.instances.get(0).name.peek());
        assertEquals("beta", rep.instances.get(1).name.peek());
        assertEquals("gamma", rep.instances.get(2).name.peek());
    }

    @Test
    void repeaterDelegateChildrenAreInstantiated() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  TestRepeater {\n" +
            "    id: rep\n" +
            "    model: 2\n" +
            "    TestItem {\n" +
            "      width: index + 100\n" +
            "      TestItem { name: \"leaf\" }\n" +
            "    }\n" +
            "  }\n" +
            "}");
        TestRepeater rep = (TestRepeater) it.getClass().getField("rep").get(it);
        assertEquals(2, rep.instances.size());
        assertEquals(100L, rep.instances.get(0).width.peek().longValue());
        assertEquals(101L, rep.instances.get(1).width.peek().longValue());
        assertEquals(1, rep.instances.get(0).children.size());
        assertEquals("leaf", rep.instances.get(0).children.get(0).name.peek());
    }

    @Test
    void repeaterListIntegerModelDataIsCoerced() throws Exception {
        TestItem it = instantiate(
            "TestItem {\n" +
            "  TestRepeater {\n" +
            "    id: rep\n" +
            "    model: [10, 20, 30]\n" +
            "    TestItem { width: modelData }\n" +
            "  }\n" +
            "}");
        TestRepeater rep = (TestRepeater) it.getClass().getField("rep").get(it);
        assertEquals(3, rep.instances.size());
        assertEquals(10L, rep.instances.get(0).width.peek().longValue());
        assertEquals(20L, rep.instances.get(1).width.peek().longValue());
        assertEquals(30L, rep.instances.get(2).width.peek().longValue());
    }
}
