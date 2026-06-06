package io.qml4j.compiler.bytecode.emit;

import io.qml4j.compiler.bytecode.AliasRef;
import io.qml4j.compiler.bytecode.QmlCompiler;
import io.qml4j.engine.QObject;
import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.qml4j.compiler.bytecode.asm.Bytecode.pushStringArray;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.QOBJECT_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_HANDLER_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_RELAY_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Fields.findPropertyField;
import static io.qml4j.compiler.bytecode.asm.Fields.findSignalFieldOrNull;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.collectAliases;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.collectSingletons;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.pushAliases;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.pushSingletons;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.require;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.validateSource;

// Static helpers for emitting signal handlers, property-change handlers, and
// function registrations in a generated constructor. All Rhino-backed — there
// is no ASM handler fallback.
public final class HandlerEmitter {

    private HandlerEmitter() {}

    static final String RHINO_HANDLER_INTERNAL = "io/qml4j/engine/js/RhinoHandler";
    static final String RHINO_FUNCTION_INTERNAL = "io/qml4j/engine/js/RhinoFunction";

    // Returns the signal name for an `onFoo` handler key, or null if the key is
    // not a valid handler name (must start with "on" followed by an uppercase letter).
    public static String signalNameFromHandler(String key) {
        if (key.length() < 3 || !key.startsWith("on")) return null;
        char c = key.charAt(2);
        if (!Character.isUpperCase(c)) return null;
        return Character.toLowerCase(c) + key.substring(3);
    }

    // Connects a RhinoHandler to the named signal on the Keys attached object.
    public static void emitKeysHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                       int outerLocal, String componentBinaryName,
                                       int[] handlerCounter, int[] bindingCounter,
                                       Map<String, byte[]> classes, String signalName,
                                       Ast.Statement body, String source,
                                       Map<String, Class<? extends QObject>> idTypes,
                                       Map<String, String> declaredProps,
                                       Map<String, AliasRef> aliases,
                                       Map<String, Integer> rootFunctions,
                                       Set<String> customSignals) {
        Method keysMethod;
        try {
            keysMethod = outerType.getMethod("keys");
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(
                "type " + outerType.getName() + " does not support Keys attached handlers");
        }
        Class<?> keysType = keysMethod.getReturnType();
        Field signalField = findSignalFieldOrNull(keysType, signalName);
        if (signalField == null) {
            throw new IllegalArgumentException("Keys has no signal '" + signalName + "'");
        }
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');
        String keysOwnerInternal = Type.getInternalName(keysMethod.getDeclaringClass());
        String keysTypeInternal = Type.getInternalName(keysType);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, keysOwnerInternal, "keys",
                             "()L" + keysTypeInternal + ";", false);
        ctor.visitFieldInsn(Opcodes.GETFIELD, keysTypeInternal, signalName, SIGNAL_DESC);
        emitHandlerInstance(ctor, outerType, outerInternal, componentInternal, componentBinaryName,
                            outerLocal, body, source, Collections.singletonList("event"),
                            idTypes, declaredProps, aliases, rootFunctions, customSignals);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    // Connects a RhinoHandler to the given signal field.
    public static void emitSignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                         int outerLocal, String componentBinaryName,
                                         int[] handlerCounter, int[] bindingCounter,
                                         Map<String, byte[]> classes, Field signalField,
                                         Ast.Statement body, String source,
                                         Map<String, Class<? extends QObject>> idTypes,
                                         List<String> signalParams,
                                         Map<String, String> declaredProps,
                                         Map<String, AliasRef> aliases,
                                         Map<String, Integer> rootFunctions,
                                         Set<String> customSignals) {
        String declOwner = Type.getInternalName(signalField.getDeclaringClass());
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, signalField.getName(), SIGNAL_DESC);
        emitHandlerInstance(ctor, outerType, outerInternal, componentInternal, componentBinaryName,
                            outerLocal, body, source, signalParams, idTypes, declaredProps, aliases,
                            rootFunctions, customSignals);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    // Registers a RhinoHandler as a property-change handler on propName.
    public static void emitPropertyChangeHandler(MethodVisitor ctor,
                                                  Class<? extends QObject> outerType,
                                                  int outerLocal, String componentBinaryName,
                                                  int[] handlerCounter, int[] bindingCounter,
                                                  Map<String, byte[]> classes, String propName,
                                                  Ast.Statement body, String source,
                                                  Map<String, Class<? extends QObject>> idTypes,
                                                  Map<String, String> declaredProps,
                                                  Map<String, AliasRef> aliases,
                                                  Map<String, Integer> rootFunctions,
                                                  Set<String> customSignals) {
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');
        String fieldOwner;
        if (declaredProps.containsKey(propName)) {
            fieldOwner = declaredProps.get(propName);
        } else {
            fieldOwner = Type.getInternalName(
                findPropertyField(outerType, propName).getDeclaringClass());
        }

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, fieldOwner, propName, PROPERTY_DESC);
        emitHandlerInstance(ctor, outerType, outerInternal, componentInternal, componentBinaryName,
                            outerLocal, body, source, null, idTypes, declaredProps, aliases,
                            rootFunctions, customSignals);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "addChangeHandler", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    // Connects a RhinoHandler to a named signal on a SignalRelay.
    public static void emitRelaySignalHandler(MethodVisitor ctor,
                                              Class<? extends QObject> outerType,
                                              int outerLocal, String componentBinaryName,
                                              int[] handlerCounter, int[] bindingCounter,
                                              Map<String, byte[]> classes, String signalName,
                                              Ast.Statement body, String source,
                                              Map<String, Class<? extends QObject>> idTypes,
                                              List<String> signalParams,
                                              Map<String, String> declaredProps,
                                              Map<String, AliasRef> aliases,
                                              Map<String, Integer> rootFunctions,
                                              Set<String> customSignals) {
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(signalName);
        emitHandlerInstance(ctor, outerType, outerInternal, componentInternal, componentBinaryName,
                            outerLocal, body, source, signalParams, idTypes, declaredProps, aliases,
                            rootFunctions, customSignals);
        ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SIGNAL_RELAY_INTERNAL,
                             "connectSignal",
                             "(Ljava/lang/String;" + SIGNAL_HANDLER_DESC + ")V", true);
    }

    // Registers `name` on the QObject at outerLocal as a RhinoFunction callable
    // (__putFunction), reached by both bare and member calls through callQml/callMethod.
    public static void emitRhinoFunction(MethodVisitor ctor, int outerLocal,
                                         Ast.FunctionDeclaration fd,
                                         Map<String, Class<? extends QObject>> idTypes,
                                         Map<String, AliasRef> aliases) {
        validateSource(fd.source, fd.paramNames);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(fd.name);
        ctor.visitTypeInsn(Opcodes.NEW, RHINO_FUNCTION_INTERNAL);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitLdcInsn(fd.source);
        pushStringArray(ctor, fd.paramNames);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        pushStringArray(ctor, new ArrayList<>(idTypes.keySet()));
        ctor.visitInsn(QmlCompiler.inDelegateScope() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        pushSingletons(ctor, collectSingletons(fd.body));
        pushAliases(ctor, collectAliases(fd.body, aliases));
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, RHINO_FUNCTION_INTERNAL, "<init>",
            "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;Z"
            + "[Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/String;)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, QOBJECT_INTERNAL, "__putFunction",
                             "(Ljava/lang/String;Lio/qml4j/engine/Callable;)V", false);
    }

    // Pushes a RhinoHandler instance onto the stack. The signal target is expected
    // to already be on the stack below; callers connect afterwards.
    public static void emitHandlerInstance(MethodVisitor ctor, Class<?> outerType,
                                           String outerInternal, String componentInternal,
                                           String componentBinaryName, int outerLocal,
                                           Ast.Statement body, String source,
                                           List<String> signalParams,
                                           Map<String, Class<? extends QObject>> idTypes,
                                           Map<String, String> declaredProps,
                                           Map<String, AliasRef> aliases,
                                           Map<String, Integer> rootFunctions,
                                           Set<String> customSignals) {
        List<String> params = signalParams != null ? signalParams : Collections.<String>emptyList();
        boolean delegate = QmlCompiler.inDelegateScope();
        if (source == null) {
            throw new IllegalArgumentException("signal handler has no captured source");
        }
        require(body, outerType, idTypes, declaredProps, params, rootFunctions,
                customSignals, aliases);
        validateSource(source, params);
        ctor.visitTypeInsn(Opcodes.NEW, RHINO_HANDLER_INTERNAL);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitLdcInsn(source);
        pushStringArray(ctor, params);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        pushStringArray(ctor, new ArrayList<>(idTypes.keySet()));
        ctor.visitInsn(delegate ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        pushSingletons(ctor, collectSingletons(body));
        pushAliases(ctor, collectAliases(body, aliases));
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, RHINO_HANDLER_INTERNAL, "<init>",
            "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;Z"
            + "[Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/String;)V", false);
    }

    // Emits a thin reflective method `name(Object...)` that forwards to the function's
    // RhinoFunction (registered via __putFunction in the ctor). Keeps the Java-method
    // identity that callers using getClass().getMethod(name) rely on.
    public static void emitThinRootFunctionMethod(ClassWriter cw, Ast.FunctionDeclaration fd) {
        int n = fd.paramNames.size();
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < n; i++) desc.append("Ljava/lang/Object;");
        desc.append(")Ljava/lang/Object;");

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, fd.name, desc.toString(), null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(fd.name);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, QOBJECT_INTERNAL, "__getFunction",
                           "(Ljava/lang/String;)Lio/qml4j/engine/Callable;", false);
        mv.visitLdcInsn(n);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < n; i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(i);
            mv.visitVarInsn(Opcodes.ALOAD, i + 1);
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "io/qml4j/engine/Callable", "call",
                           "([Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
