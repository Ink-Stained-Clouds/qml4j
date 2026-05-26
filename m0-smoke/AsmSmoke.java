import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;

public class AsmSmoke {
    static class GenLoader extends ClassLoader {
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    public static void main(String[] args) throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC, "io/qml4j/smoke/GenHello", null, "java/lang/Object", null);

        MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "answer", "()I", null, null);
        m.visitCode();
        m.visitIntInsn(BIPUSH, 42);
        m.visitInsn(IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        java.nio.file.Files.write(java.nio.file.Paths.get("GenHello.class"), bytes);
        System.out.println("emitted " + bytes.length + " bytes");

        GenLoader cl = new GenLoader();
        Class<?> c = cl.define("io.qml4j.smoke.GenHello", bytes);
        Object r = c.getMethod("answer").invoke(null);
        System.out.println("invoked answer() = " + r);
        if (!Integer.valueOf(42).equals(r)) throw new AssertionError("expected 42, got " + r);
        System.out.println("ASM smoke PASS");
    }
}
