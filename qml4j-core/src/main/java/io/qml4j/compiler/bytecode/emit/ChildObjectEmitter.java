package io.qml4j.compiler.bytecode.emit;

import io.qml4j.compiler.bytecode.decl.DeclaredProp;
import io.qml4j.engine.QObject;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.qml4j.compiler.bytecode.asm.Descriptors;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_DESC;

// Pure-static helpers for generating child-object subclasses (anonymous classes that
// carry declared properties, custom signals, and delegate-local id fields).
public final class ChildObjectEmitter {

    private ChildObjectEmitter() {}

    // Generates a child subclass with signals and property fields only (no id fields).
    public static byte[] emitChildSubclass(String subInternal, String parentInternal,
                                           Set<String> signalNames,
                                           List<DeclaredProp> propDecls) {
        return emitChildSubclass(subInternal, parentInternal, signalNames, propDecls, null, null);
    }

    // Full variant: also adds extra interface implementations and id-keyed object fields.
    public static byte[] emitChildSubclass(String subInternal, String parentInternal,
                                           Set<String> signalNames,
                                           List<DeclaredProp> propDecls,
                                           String[] extraInterfaces,
                                           Map<String, Class<? extends QObject>> idFields) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 subInternal, null, parentInternal, extraInterfaces);
        for (String sig : signalNames) {
            cw.visitField(Opcodes.ACC_PUBLIC, sig, SIGNAL_DESC, null, null).visitEnd();
        }
        for (DeclaredProp dp : propDecls) {
            cw.visitField(Opcodes.ACC_PUBLIC, dp.name, PROPERTY_DESC,
                          Descriptors.propertyFieldSignature(dp.typeName), null).visitEnd();
        }
        if (idFields != null) {
            for (Map.Entry<String, Class<? extends QObject>> e : idFields.entrySet()) {
                cw.visitField(Opcodes.ACC_PUBLIC, e.getKey(),
                              "L" + Type.getInternalName(e.getValue()) + ";",
                              null, null).visitEnd();
            }
        }
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInternal, "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    // Validates that the type can be used as a Behavior (must expose attach(Object, String)).
    public static void verifyAttachable(Class<?> type) {
        try {
            type.getMethod("attach", Object.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                "type '" + type.getName() + "' used as Behavior must have "
                + "attach(Object, String) method");
        }
    }
}
