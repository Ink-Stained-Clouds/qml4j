package io.qml4j.compiler;

import io.qml4j.engine.Property;
import io.qml4j.engine.QObject;
import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class QmlCompiler {

    private static final String PROPERTY_INTERNAL = "io/qml4j/engine/Property";
    private static final String PROPERTY_DESC = "L" + PROPERTY_INTERNAL + ";";
    private static final String BINDING_INTERNAL = "io/qml4j/engine/Binding";
    private static final String SIGNAL_INTERNAL = "io/qml4j/engine/Signal";
    private static final String SIGNAL_DESC = "L" + SIGNAL_INTERNAL + ";";
    private static final String RUNNABLE_INTERNAL = "java/lang/Runnable";
    private static final String LIST_INTERNAL = "java/util/List";
    private static final String LIST_DESC = "L" + LIST_INTERNAL + ";";

    private final AtomicInteger componentCounter = new AtomicInteger();

    public CompiledUnit compile(Ast.QmlDocument doc, TypeRegistry registry) {
        Class<? extends QObject> rootType = registry.resolve(doc.root.typeName);
        int id = componentCounter.getAndIncrement();
        String componentBinaryName = "io.qml4j.generated.Component$" + id;
        String componentInternal = componentBinaryName.replace('.', '/');
        String rootInternal = Type.getInternalName(rootType);

        Map<String, byte[]> classes = new LinkedHashMap<>();
        int[] bindingCounter = {0};
        int[] handlerCounter = {0};
        int[] localCounter = {1};

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 componentInternal, null, rootInternal, null);

        Set<String> rootSignalNames = new LinkedHashSet<>();
        for (Ast.ObjectMember m : doc.root.members) {
            if (m instanceof Ast.SignalDeclaration) {
                Ast.SignalDeclaration sd = (Ast.SignalDeclaration) m;
                if (!rootSignalNames.add(sd.name)) {
                    throw new IllegalArgumentException("duplicate signal: " + sd.name);
                }
                if (findSignalFieldOrNull(rootType, sd.name) != null) {
                    throw new IllegalArgumentException(
                        "signal '" + sd.name + "' shadows existing field on " + rootType.getName());
                }
                cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                              sd.name, SIGNAL_DESC, null, null).visitEnd();
            }
        }

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, rootInternal, "<init>", "()V", false);
        for (String sig : rootSignalNames) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitTypeInsn(Opcodes.NEW, SIGNAL_INTERNAL);
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, SIGNAL_INTERNAL, "<init>", "()V", false);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, sig, SIGNAL_DESC);
        }

        emitObjectBody(ctor, rootType, 0, doc.root, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       componentInternal, rootSignalNames);

        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        cw.visitEnd();

        classes.put(componentBinaryName, cw.toByteArray());
        return new CompiledUnit(componentBinaryName, classes);
    }

    private void emitObjectBody(MethodVisitor ctor, Class<? extends QObject> outerType,
                                int outerLocal, Ast.ObjectNode obj, TypeRegistry registry,
                                int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                Map<String, byte[]> classes, String componentBinaryName,
                                String customSignalOwner, Set<String> customSignals) {
        boolean isRoot = outerLocal == 0;
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.SignalDeclaration) {
                if (!isRoot) {
                    throw new UnsupportedOperationException(
                        "signal declarations are only supported on the root object");
                }
                continue;
            }
            emitMember(ctor, outerType, outerLocal, m, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       isRoot ? customSignalOwner : null,
                       isRoot ? customSignals : Collections.<String>emptySet());
        }
    }

    private void emitMember(MethodVisitor ctor, Class<? extends QObject> outerType,
                            int outerLocal, Ast.ObjectMember m, TypeRegistry registry,
                            int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                            Map<String, byte[]> classes, String componentBinaryName,
                            String customSignalOwner, Set<String> customSignals) {
        if (m instanceof Ast.PropertyBinding) {
            Ast.PropertyBinding b = (Ast.PropertyBinding) m;
            List<String> path = b.path;
            if (path.size() == 1 && "id".equals(path.get(0))) return;
            if (!(b.value instanceof Ast.ExpressionValue)) {
                throw new UnsupportedOperationException("only expression bindings supported");
            }
            Ast.Expression e = ((Ast.ExpressionValue) b.value).expr;
            if (path.size() == 1) {
                String key = path.get(0);
                String signalName = signalNameFromHandler(key);
                if (signalName != null && customSignals.contains(signalName)) {
                    emitCustomSignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                            handlerCounter, classes,
                                            customSignalOwner, signalName, e);
                    return;
                }
                Field signalField = signalName != null ? findSignalFieldOrNull(outerType, signalName) : null;
                if (signalField != null) {
                    emitSignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                      handlerCounter, classes, signalField, e);
                    return;
                }
                if (e instanceof Ast.LiteralExpr) {
                    emitLiteralAssignment(ctor, outerType, outerLocal, key, (Ast.LiteralExpr) e);
                } else {
                    emitExpressionBinding(ctor, outerType, outerLocal, componentBinaryName,
                                          bindingCounter, classes, key, e);
                }
                return;
            }
            if (path.size() == 2) {
                emitGroupedBinding(ctor, outerType, outerLocal, componentBinaryName,
                                   bindingCounter, classes, path.get(0), path.get(1), e);
                return;
            }
            throw new UnsupportedOperationException("nested grouped property path not supported: " + path);
        }
        if (m instanceof Ast.ChildObject) {
            emitChildObject(ctor, outerType, outerLocal, ((Ast.ChildObject) m).object, registry,
                            localCounter, bindingCounter, handlerCounter, classes, componentBinaryName);
            return;
        }
        if (m instanceof Ast.SignalDeclaration) {
            throw new UnsupportedOperationException(
                "signal declarations are only supported on the root object");
        }
        if (m instanceof Ast.PropertyDeclaration) {
            throw new UnsupportedOperationException("property declarations not yet supported");
        }
        throw new IllegalStateException("unknown member: " + m.getClass());
    }

    private void emitChildObject(MethodVisitor ctor, Class<? extends QObject> outerType,
                                 int outerLocal, Ast.ObjectNode child, TypeRegistry registry,
                                 int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                 Map<String, byte[]> classes, String componentBinaryName) {
        Class<? extends QObject> childType = registry.resolve(child.typeName);
        String childInternal = Type.getInternalName(childType);
        int childLocal = localCounter[0]++;

        ctor.visitTypeInsn(Opcodes.NEW, childInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, childInternal, "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ASTORE, childLocal);

        Field parentProp = findPropertyFieldOrNull(childType, "parent");
        if (parentProp != null) {
            String declOwner = Type.getInternalName(parentProp.getDeclaringClass());
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, "parent", PROPERTY_DESC);
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                 "set", "(Ljava/lang/Object;)V", false);
        }

        Field childrenField = findListFieldOrNull(outerType, "children");
        if (childrenField != null) {
            String declOwner = Type.getInternalName(childrenField.getDeclaringClass());
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, "children", LIST_DESC);
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_INTERNAL,
                                 "add", "(Ljava/lang/Object;)Z", true);
            ctor.visitInsn(Opcodes.POP);
        }

        emitObjectBody(ctor, childType, childLocal, child, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       null, Collections.<String>emptySet());
    }

    private static Field findPropertyField(Class<?> outerType, String name) {
        Field f;
        try {
            f = outerType.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                "no public field '" + name + "' on " + outerType.getName());
        }
        if (!Property.class.isAssignableFrom(f.getType())) {
            throw new IllegalArgumentException(
                "field '" + name + "' on " + outerType.getName() + " is not a Property");
        }
        return f;
    }

    private static Field findPropertyFieldOrNull(Class<?> type, String name) {
        try {
            Field f = type.getField(name);
            return Property.class.isAssignableFrom(f.getType()) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Field findListFieldOrNull(Class<?> type, String name) {
        try {
            Field f = type.getField(name);
            return java.util.List.class.isAssignableFrom(f.getType()) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private void emitLiteralAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                       int outerLocal, String propName, Ast.LiteralExpr lit) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, propName, PROPERTY_DESC);
        loadLiteral(ctor, lit);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "set", "(Ljava/lang/Object;)V", false);
    }

    private void emitExpressionBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
                                       int outerLocal, String componentBinaryName,
                                       int[] bindingCounter, Map<String, byte[]> classes,
                                       String propName, Ast.Expression expr) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        String outerInternal = Type.getInternalName(outerType);

        int n = bindingCounter[0]++;
        String bindingBinaryName = componentBinaryName + "$Binding$" + n;
        String bindingInternal = bindingBinaryName.replace('.', '/');

        byte[] bindingBytes = emitBindingClass(bindingInternal, outerInternal, outerType, expr);
        classes.put(bindingBinaryName, bindingBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, propName, PROPERTY_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, bindingInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, bindingInternal, "<init>",
                             "(L" + outerInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "bind", "(L" + BINDING_INTERNAL + ";)V", false);
    }

    private void emitGroupedBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
                                    int outerLocal, String componentBinaryName,
                                    int[] bindingCounter, Map<String, byte[]> classes,
                                    String groupName, String propName, Ast.Expression expr) {
        Field groupField;
        try {
            groupField = outerType.getField(groupName);
        } catch (NoSuchFieldException ex) {
            throw new IllegalArgumentException(
                "no group field '" + groupName + "' on " + outerType.getName());
        }
        if (Property.class.isAssignableFrom(groupField.getType())) {
            throw new IllegalArgumentException(
                "field '" + groupName + "' on " + outerType.getName()
                + " is a Property, not a grouped object");
        }
        Class<?> groupType = groupField.getType();
        Field propField = findPropertyField(groupType, propName);
        String groupDeclOwner = Type.getInternalName(groupField.getDeclaringClass());
        String propDeclOwner = Type.getInternalName(propField.getDeclaringClass());
        String groupTypeInternal = Type.getInternalName(groupType);
        String outerInternal = Type.getInternalName(outerType);

        if (expr instanceof Ast.LiteralExpr) {
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, groupDeclOwner, groupName,
                                "L" + groupTypeInternal + ";");
            ctor.visitFieldInsn(Opcodes.GETFIELD, propDeclOwner, propName, PROPERTY_DESC);
            loadLiteral(ctor, (Ast.LiteralExpr) expr);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                 "set", "(Ljava/lang/Object;)V", false);
            return;
        }

        int n = bindingCounter[0]++;
        String bindingBinaryName = componentBinaryName + "$Binding$" + n;
        String bindingInternal = bindingBinaryName.replace('.', '/');
        byte[] bindingBytes = emitBindingClass(bindingInternal, outerInternal, outerType, expr);
        classes.put(bindingBinaryName, bindingBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, groupDeclOwner, groupName,
                            "L" + groupTypeInternal + ";");
        ctor.visitFieldInsn(Opcodes.GETFIELD, propDeclOwner, propName, PROPERTY_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, bindingInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, bindingInternal, "<init>",
                             "(L" + outerInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "bind", "(L" + BINDING_INTERNAL + ";)V", false);
    }

    private static String signalNameFromHandler(String key) {
        if (key.length() < 3 || !key.startsWith("on")) return null;
        char c = key.charAt(2);
        if (!Character.isUpperCase(c)) return null;
        return Character.toLowerCase(c) + key.substring(3);
    }

    private static Field findSignalFieldOrNull(Class<?> type, String name) {
        try {
            Field f = type.getField(name);
            return io.qml4j.engine.Signal.class.isAssignableFrom(f.getType()) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private void emitCustomSignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                         int outerLocal, String componentBinaryName,
                                         int[] handlerCounter, Map<String, byte[]> classes,
                                         String signalOwnerInternal, String signalName,
                                         Ast.Expression body) {
        String outerInternal = Type.getInternalName(outerType);
        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');
        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body);
        classes.put(handlerBinaryName, handlerBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, signalOwnerInternal, signalName, SIGNAL_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, handlerInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, handlerInternal, "<init>",
                             "(L" + outerInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(L" + RUNNABLE_INTERNAL + ";)V", false);
    }

    private void emitSignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                   int outerLocal, String componentBinaryName,
                                   int[] handlerCounter, Map<String, byte[]> classes,
                                   Field signalField, Ast.Expression body) {
        String declOwner = Type.getInternalName(signalField.getDeclaringClass());
        String outerInternal = Type.getInternalName(outerType);

        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');

        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body);
        classes.put(handlerBinaryName, handlerBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, signalField.getName(), SIGNAL_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, handlerInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, handlerInternal, "<init>",
                             "(L" + outerInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(L" + RUNNABLE_INTERNAL + ";)V", false);
    }

    private byte[] emitHandlerClass(String handlerInternal, String outerInternal,
                                    Class<?> outerType, Ast.Expression body) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 handlerInternal, null, "java/lang/Object",
                 new String[]{RUNNABLE_INTERNAL});

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                      "outer", "L" + outerInternal + ";", null, null).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                                            "(L" + outerInternal + ";)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, handlerInternal, "outer", "L" + outerInternal + ";");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        run.visitCode();
        ExpressionCodegen codegen = new ExpressionCodegen(outerInternal, handlerInternal, outerType);
        codegen.emit(run, body);
        run.visitInsn(Opcodes.POP);
        run.visitInsn(Opcodes.RETURN);
        run.visitMaxs(0, 0);
        run.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] emitBindingClass(String bindingInternal, String outerInternal,
                                    Class<?> outerType, Ast.Expression expr) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 bindingInternal, null, BINDING_INTERNAL, null);

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                      "outer", "L" + outerInternal + ";", null, null).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                                            "(L" + outerInternal + ";)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, BINDING_INTERNAL, "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, bindingInternal, "outer", "L" + outerInternal + ";");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor eval = cw.visitMethod(Opcodes.ACC_PUBLIC, "evaluate",
                                            "()Ljava/lang/Object;", null, null);
        eval.visitCode();
        ExpressionCodegen codegen = new ExpressionCodegen(outerInternal, bindingInternal, outerType);
        codegen.emit(eval, expr);
        eval.visitInsn(Opcodes.ARETURN);
        eval.visitMaxs(0, 0);
        eval.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void loadLiteral(MethodVisitor mv, Ast.LiteralExpr lit) {
        switch (lit.kind) {
            case INT:
                mv.visitLdcInsn((Long) lit.value);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long",
                                   "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case FLOAT:
                mv.visitLdcInsn((Double) lit.value);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double",
                                   "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case STRING:
                mv.visitLdcInsn((String) lit.value);
                break;
            case BOOL:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean",
                                  ((Boolean) lit.value) ? "TRUE" : "FALSE",
                                  "Ljava/lang/Boolean;");
                break;
            case NULL:
            case UNDEFINED:
                mv.visitInsn(Opcodes.ACONST_NULL);
                break;
            default:
                throw new IllegalStateException("literal kind: " + lit.kind);
        }
    }
}
