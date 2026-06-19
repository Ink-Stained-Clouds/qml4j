package io.github.timer_err.qml4j.compiler;

import io.github.timer_err.qml4j.compiler.bytecode.QmlCompiler;
import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.engine.classloader.JvmClassLoaderBackend;
import io.github.timer_err.qml4j.parser.Qml4j;
import io.github.timer_err.qml4j.parser.ast.Ast;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A signal's parameter names are stamped on its compiled field (@SignalParams), so
// a non-arrow handler in another file — which only sees the component via reflection
// — still binds the args by name. Mirrors md3 SegmentedButton's `signal clicked(int
// index)` consumed from an app page as `onClicked: ... index ...`.
class CrossFileSignalParamTest {

    private static final QmlCompiler COMPILER = new QmlCompiler();

    private static Class<?> compileAndDefine(String qml, TypeRegistry reg, JvmClassLoaderBackend backend) {
        Ast.QmlDocument doc = Qml4j.parse(qml);
        CompiledUnit unit = COMPILER.compile(doc, reg);
        Class<?> root = null;
        for (Map.Entry<String, byte[]> e : unit.classes().entrySet()) {
            Class<?> c = backend.defineClass(e.getKey(), e.getValue());
            if (e.getKey().equals(unit.rootClassName())) root = c;
        }
        return root;
    }

    @Test
    void nonArrowHandlerBindsParamFromAnotherFile() throws Exception {
        JvmClassLoaderBackend backend = new JvmClassLoaderBackend();
        TypeRegistry reg = new TypeRegistry().register("TestItem", QmlCompilerTest.TestItem.class);

        // File A: a reusable component declaring a parameterised signal.
        Class<?> compA = compileAndDefine(
            "TestItem { signal clicked(int index) }", reg, backend);
        reg.register("CompA", compA.asSubclass(QObject.class));

        // File B: uses A and reads the signal arg by name (no arrow params).
        Class<?> compB = compileAndDefine(
            "CompA { property int got: -1; onClicked: got = index }", reg, backend);

        Object b = compB.getDeclaredConstructor().newInstance();
        Signal clicked = (Signal) b.getClass().getField("clicked").get(b);
        clicked.emit(42);

        Property<?> got = (Property<?>) b.getClass().getField("got").get(b);
        assertEquals(42L, ((Number) got.peek()).longValue());
    }
}
