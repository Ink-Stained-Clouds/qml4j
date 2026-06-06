package io.qml4j.compiler.bytecode.emit;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static io.qml4j.compiler.bytecode.asm.Descriptors.DELEGATE_FACTORY_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.QOBJECT_INTERNAL;

// Pure-static helpers for generating the DelegateFactory class that a DelegateHost
// uses to instantiate delegate objects.
public final class DelegateEmitter {

    private DelegateEmitter() {}

    // Generates a DelegateFactory class that holds a reference to the root component
    // and delegates `create(int, Object, Object)` to the component's `_delegate$n` method.
    public static byte[] emitDelegateFactoryClass(String factoryBinaryName,
                                                   String componentInternal, int n) {
        String factoryInternal = factoryBinaryName.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 factoryInternal, null, "java/lang/Object",
                 new String[]{DELEGATE_FACTORY_INTERNAL});
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                      "root", "L" + componentInternal + ";", null, null).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                                            "(L" + componentInternal + ";)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, factoryInternal, "root",
                            "L" + componentInternal + ";");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor create = cw.visitMethod(Opcodes.ACC_PUBLIC, "create",
            "(ILjava/lang/Object;Ljava/lang/Object;)L" + QOBJECT_INTERNAL + ";", null, null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 0);
        create.visitFieldInsn(Opcodes.GETFIELD, factoryInternal, "root",
                              "L" + componentInternal + ";");
        create.visitVarInsn(Opcodes.ILOAD, 1);
        create.visitVarInsn(Opcodes.ALOAD, 2);
        create.visitVarInsn(Opcodes.ALOAD, 3);
        create.visitMethodInsn(Opcodes.INVOKEVIRTUAL, componentInternal, "_delegate$" + n,
                               "(ILjava/lang/Object;Ljava/lang/Object;)L" + QOBJECT_INTERNAL + ";",
                               false);
        create.visitInsn(Opcodes.ARETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
