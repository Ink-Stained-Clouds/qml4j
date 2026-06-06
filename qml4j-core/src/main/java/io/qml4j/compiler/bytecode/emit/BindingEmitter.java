package io.qml4j.compiler.bytecode.emit;

import io.qml4j.compiler.bytecode.AliasRef;
import io.qml4j.compiler.bytecode.CompileScope;
import io.qml4j.engine.QObject;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Map;

import static io.qml4j.compiler.bytecode.asm.Bytecode.pushStringArray;
import static io.qml4j.compiler.bytecode.asm.Descriptors.BINDING_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_INTERNAL;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.pushAliases;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.pushSingletons;

// Static helpers that build and wire RhinoBinding instances in a generated
// constructor. Three levels of abstraction:
//   pushNewRhinoBinding  — leaves a new RhinoBinding on the operand stack
//   emitRhinoBindingFor  — binds the Property already on the operand stack
//   emitRhinoBindingBind — loads a named field and binds it
public final class BindingEmitter {

    private BindingEmitter() {}

    static final String RHINO_BINDING_INTERNAL = "io/qml4j/engine/js/RhinoBinding";

    // Leaves a fresh RhinoBinding(source, outer, root, ids, isDelegate, singletons, aliases)
    // on the operand stack. The calling constructor then invokes bind() or addChangeBinding().
    public static void pushNewRhinoBinding(MethodVisitor ctor, String source, int outerLocal,
                                           Map<String, Class<? extends QObject>> idTypes,
                                           Map<String, Class<? extends QObject>> singletons,
                                           Map<String, AliasRef> aliases) {
        ctor.visitTypeInsn(Opcodes.NEW, RHINO_BINDING_INTERNAL);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitLdcInsn(source);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        pushStringArray(ctor, new ArrayList<>(idTypes.keySet()));
        ctor.visitInsn(CompileScope.inDelegateScope() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        pushSingletons(ctor, singletons);
        pushAliases(ctor, aliases);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, RHINO_BINDING_INTERNAL, "<init>",
            "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;Z"
            + "[Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/String;)V", false);
    }

    // With the target Property already on the operand stack, creates a RhinoBinding and
    // calls Property.bind(). Lets callers load any Property (a declared field or a
    // grouped border.color/font.pixelSize) and reuse the same wiring.
    public static void emitRhinoBindingFor(MethodVisitor ctor, String source, int outerLocal,
                                           Map<String, Class<? extends QObject>> idTypes,
                                           Map<String, Class<? extends QObject>> singletons,
                                           Map<String, AliasRef> aliases) {
        pushNewRhinoBinding(ctor, source, outerLocal, idTypes, singletons, aliases);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "bind", "(L" + BINDING_INTERNAL + ";)V", false);
    }

    // Loads the named property field from declOwner and binds it to a new RhinoBinding.
    public static void emitRhinoBindingBind(MethodVisitor ctor, String declOwner, String propName,
                                            String source, int outerLocal,
                                            Map<String, Class<? extends QObject>> idTypes,
                                            Map<String, Class<? extends QObject>> singletons,
                                            Map<String, AliasRef> aliases) {
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, propName, PROPERTY_DESC);
        emitRhinoBindingFor(ctor, source, outerLocal, idTypes, singletons, aliases);
    }
}
