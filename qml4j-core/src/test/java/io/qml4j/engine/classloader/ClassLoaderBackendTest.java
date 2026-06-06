package io.qml4j.engine.classloader;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.QObject;
import io.qml4j.engine.Signal;
import io.qml4j.engine.Context;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClassLoaderBackendTest {

    @Test
    void jvmBackendDefinesAndLoadsClass() throws Exception {
        String binaryName = "io.qml4j.engine.gen.Answer";
        byte[] bytes = emitAnswerClass(binaryName);

        ClassLoaderBackend backend = new JvmClassLoaderBackend();
        Class<?> klass = backend.defineClass(binaryName, bytes);
        assertNotNull(klass);
        assertEquals(binaryName, klass.getName());

        Method m = klass.getMethod("answer");
        Object result = m.invoke(klass.getDeclaredConstructor().newInstance());
        assertEquals(42, result);
    }

    @Test
    void engineHasDefaultBackend() {
        QmlEngine e = new QmlEngine();
        assertNotNull(e.backend());
        assertNotNull(e.rootContext());
    }

    @Test
    void engineUsesProvidedBackend() {
        ClassLoaderBackend custom = (name, bytes) -> {
            throw new UnsupportedOperationException("not used");
        };
        QmlEngine e = new QmlEngine(custom);
        assertSame(custom, e.backend());
    }

    /** Emits: public class X { public X(){} public int answer(){ return 42; } } */
    private static byte[] emitAnswerClass(String binaryName) {
        String internal = binaryName.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, "answer", "()I", null, null);
        m.visitCode();
        m.visitIntInsn(Opcodes.BIPUSH, 42);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
