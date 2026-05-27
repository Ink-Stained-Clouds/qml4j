package io.qml4j.compiler.bytecode;

import io.qml4j.compiler.CompiledUnit;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.engine.PropertyChangeSink;
import io.qml4j.engine.binding.Property;
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

    private static final String PROPERTY_INTERNAL = "io/qml4j/engine/binding/Property";
    private static final String PROPERTY_DESC = "L" + PROPERTY_INTERNAL + ";";
    private static final String BINDING_INTERNAL = "io/qml4j/engine/binding/Binding";
    private static final String SIGNAL_INTERNAL = "io/qml4j/engine/Signal";
    private static final String SIGNAL_DESC = "L" + SIGNAL_INTERNAL + ";";
    private static final String RUNNABLE_INTERNAL = "java/lang/Runnable";
    private static final String SIGNAL_HANDLER_INTERNAL = "io/qml4j/engine/SignalHandler";
    private static final String SIGNAL_HANDLER_DESC = "L" + SIGNAL_HANDLER_INTERNAL + ";";
    private static final String LIST_INTERNAL = "java/util/List";
    private static final String LIST_DESC = "L" + LIST_INTERNAL + ";";
    private static final String SINK_INTERNAL = "io/qml4j/engine/PropertyChangeSink";

    private final AtomicInteger componentCounter = new AtomicInteger();

    public CompiledUnit compile(Ast.QmlDocument doc, TypeRegistry registry) {
        Class<? extends QObject> rootType = registry.resolve(doc.root.typeName);
        int id = componentCounter.getAndIncrement();
        String componentBinaryName = "io.qml4j.generated.Component$" + id;
        String componentInternal = componentBinaryName.replace('.', '/');
        String rootInternal = Type.getInternalName(rootType);

        Map<String, Class<? extends QObject>> idTypes = new LinkedHashMap<>();
        collectIds(doc.root, registry, idTypes);

        Map<String, byte[]> classes = new LinkedHashMap<>();
        int[] bindingCounter = {0};
        int[] handlerCounter = {0};
        int[] localCounter = {1};

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 componentInternal, null, rootInternal, null);

        for (Map.Entry<String, Class<? extends QObject>> e : idTypes.entrySet()) {
            cw.visitField(Opcodes.ACC_PUBLIC,
                          e.getKey(), "L" + Type.getInternalName(e.getValue()) + ";",
                          null, null).visitEnd();
        }

        Set<String> rootSignalNames = new LinkedHashSet<>();
        Map<String, List<String>> customSignalParams = new LinkedHashMap<>();
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
                customSignalParams.put(sd.name, sd.paramNames);
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

        String rootId = idOf(doc.root);
        if (rootId != null) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, rootId,
                                "L" + Type.getInternalName(rootType) + ";");
        }

        emitObjectBody(ctor, rootType, 0, doc.root, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       componentInternal, rootSignalNames, customSignalParams, idTypes);

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
                                String customSignalOwner, Set<String> customSignals,
                                Map<String, List<String>> customSignalParams,
                                Map<String, Class<? extends QObject>> idTypes) {
        List<Ast.ObjectMember> deferred = new ArrayList<>();
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.SignalDeclaration) continue;
            if (isStateAssignment(m)) { deferred.add(m); continue; }
            emitMember(ctor, outerType, outerLocal, m, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       customSignalOwner, customSignals, customSignalParams,
                       idTypes);
        }
        for (Ast.ObjectMember m : deferred) {
            emitMember(ctor, outerType, outerLocal, m, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       customSignalOwner, customSignals, customSignalParams,
                       idTypes);
        }
    }

    private static boolean isStateAssignment(Ast.ObjectMember m) {
        if (!(m instanceof Ast.PropertyBinding)) return false;
        Ast.PropertyBinding b = (Ast.PropertyBinding) m;
        return b.path.size() == 1 && "state".equals(b.path.get(0));
    }

    private void emitMember(MethodVisitor ctor, Class<? extends QObject> outerType,
                            int outerLocal, Ast.ObjectMember m, TypeRegistry registry,
                            int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                            Map<String, byte[]> classes, String componentBinaryName,
                            String customSignalOwner, Set<String> customSignals,
                            Map<String, List<String>> customSignalParams,
                            Map<String, Class<? extends QObject>> idTypes) {
        if (m instanceof Ast.PropertyBinding) {
            Ast.PropertyBinding b = (Ast.PropertyBinding) m;
            List<String> path = b.path;
            if (path.size() == 1 && "id".equals(path.get(0))) return;
            if (b.value instanceof Ast.ObjectListValue) {
                if (path.size() != 1) {
                    throw new UnsupportedOperationException(
                        "object list assignment to nested path not supported: " + path);
                }
                emitObjectListAssignment(ctor, outerType, outerLocal,
                                         (Ast.ObjectListValue) b.value, registry,
                                         localCounter, bindingCounter, handlerCounter,
                                         classes, componentBinaryName, idTypes,
                                         customSignalParams, path.get(0));
                return;
            }
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
                                            customSignalOwner, signalName, e, idTypes,
                                            customSignalParams.get(signalName));
                    return;
                }
                Field signalField = signalName != null ? findSignalFieldOrNull(outerType, signalName) : null;
                if (signalField != null) {
                    emitSignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                      handlerCounter, classes, signalField, e, idTypes);
                    return;
                }
                if (PropertyChangeSink.class.isAssignableFrom(outerType) && !"target".equals(key)) {
                    emitChangeSinkAssignment(ctor, outerType, outerLocal, componentBinaryName,
                                             bindingCounter, classes, key, e, idTypes);
                    return;
                }
                if (e instanceof Ast.LiteralExpr) {
                    emitLiteralAssignment(ctor, outerType, outerLocal, key, (Ast.LiteralExpr) e);
                } else {
                    emitExpressionBinding(ctor, outerType, outerLocal, componentBinaryName,
                                          bindingCounter, classes, key, e, idTypes);
                }
                return;
            }
            if (path.size() == 2) {
                emitGroupedBinding(ctor, outerType, outerLocal, componentBinaryName,
                                   bindingCounter, classes, path.get(0), path.get(1), e, idTypes);
                return;
            }
            throw new UnsupportedOperationException("nested grouped property path not supported: " + path);
        }
        if (m instanceof Ast.ChildObject) {
            emitChildObject(ctor, outerType, outerLocal, ((Ast.ChildObject) m).object, registry,
                            localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                            idTypes, customSignalParams);
            return;
        }
        if (m instanceof Ast.BehaviorMember) {
            emitBehaviorMember(ctor, outerType, outerLocal, (Ast.BehaviorMember) m, registry,
                               localCounter, bindingCounter, handlerCounter, classes,
                               componentBinaryName, idTypes, customSignalParams);
            return;
        }
        if (m instanceof Ast.SignalDeclaration) {
            throw new IllegalStateException("signal declaration should be handled at object scope");
        }
        if (m instanceof Ast.PropertyDeclaration) {
            throw new UnsupportedOperationException("property declarations not yet supported");
        }
        throw new IllegalStateException("unknown member: " + m.getClass());
    }

    private void emitChildObject(MethodVisitor ctor, Class<? extends QObject> outerType,
                                 int outerLocal, Ast.ObjectNode child, TypeRegistry registry,
                                 int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                 Map<String, byte[]> classes, String componentBinaryName,
                                 Map<String, Class<? extends QObject>> idTypes,
                                 Map<String, List<String>> outerSignalParams) {
        emitChildObjectInto(ctor, outerType, outerLocal, child, registry,
                            localCounter, bindingCounter, handlerCounter, classes,
                            componentBinaryName, idTypes, outerSignalParams, "children");
    }

    private void emitChildObjectInto(MethodVisitor ctor, Class<? extends QObject> outerType,
                                     int outerLocal, Ast.ObjectNode child, TypeRegistry registry,
                                     int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                     Map<String, byte[]> classes, String componentBinaryName,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, List<String>> outerSignalParams,
                                     String listFieldName) {
        Class<? extends QObject> childType = registry.resolve(child.typeName);
        String parentInternal = Type.getInternalName(childType);
        String componentInternal = componentBinaryName.replace('.', '/');

        Set<String> childSignals = new LinkedHashSet<>();
        Map<String, List<String>> childSignalParams = new LinkedHashMap<>();
        for (Ast.ObjectMember m : child.members) {
            if (m instanceof Ast.SignalDeclaration) {
                Ast.SignalDeclaration sd = (Ast.SignalDeclaration) m;
                if (!childSignals.add(sd.name)) {
                    throw new IllegalArgumentException("duplicate signal: " + sd.name);
                }
                if (findSignalFieldOrNull(childType, sd.name) != null) {
                    throw new IllegalArgumentException(
                        "signal '" + sd.name + "' shadows existing field on " + childType.getName());
                }
                childSignalParams.put(sd.name, sd.paramNames);
            }
        }

        String childInternal;
        String childSignalOwner;
        if (childSignals.isEmpty()) {
            childInternal = parentInternal;
            childSignalOwner = null;
        } else {
            String childSubBinaryName = componentBinaryName + "$Child$" + localCounter[0];
            childInternal = childSubBinaryName.replace('.', '/');
            byte[] subBytes = emitChildSubclass(childInternal, parentInternal, childSignals);
            classes.put(childSubBinaryName, subBytes);
            childSignalOwner = childInternal;
        }

        int childLocal = localCounter[0]++;

        ctor.visitTypeInsn(Opcodes.NEW, childInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, childInternal, "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ASTORE, childLocal);

        for (String sig : childSignals) {
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitTypeInsn(Opcodes.NEW, SIGNAL_INTERNAL);
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, SIGNAL_INTERNAL, "<init>", "()V", false);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, childInternal, sig, SIGNAL_DESC);
        }

        String childId = idOf(child);
        if (childId != null) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, childId,
                                "L" + parentInternal + ";");
        }

        Field parentProp = findPropertyFieldOrNull(childType, "parent");
        if (parentProp != null) {
            String declOwner = Type.getInternalName(parentProp.getDeclaringClass());
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, "parent", PROPERTY_DESC);
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                 "set", "(Ljava/lang/Object;)V", false);
        }

        Field listField = findListFieldOrNull(outerType, listFieldName);
        if (listField != null) {
            String declOwner = Type.getInternalName(listField.getDeclaringClass());
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, listFieldName, LIST_DESC);
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_INTERNAL,
                                 "add", "(Ljava/lang/Object;)Z", true);
            ctor.visitInsn(Opcodes.POP);
        }

        emitObjectBody(ctor, childType, childLocal, child, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       childSignalOwner, childSignals, childSignalParams, idTypes);
    }

    private byte[] emitChildSubclass(String subInternal, String parentInternal,
                                     Set<String> signalNames) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 subInternal, null, parentInternal, null);
        for (String sig : signalNames) {
            cw.visitField(Opcodes.ACC_PUBLIC, sig, SIGNAL_DESC, null, null).visitEnd();
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

    private void emitBehaviorMember(MethodVisitor ctor, Class<? extends QObject> outerType,
                                    int outerLocal, Ast.BehaviorMember bm, TypeRegistry registry,
                                    int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                    Map<String, byte[]> classes, String componentBinaryName,
                                    Map<String, Class<? extends QObject>> idTypes,
                                    Map<String, List<String>> outerSignalParams) {
        Class<? extends QObject> behaviorType = registry.resolve(bm.typeName);
        verifyAttachable(behaviorType);
        Ast.ObjectNode synth = new Ast.ObjectNode(bm.typeName, bm.members);
        int behaviorLocal = localCounter[0];
        emitChildObjectInto(ctor, outerType, outerLocal, synth, registry,
                            localCounter, bindingCounter, handlerCounter, classes,
                            componentBinaryName, idTypes, outerSignalParams, "children");

        String behaviorInternal = Type.getInternalName(behaviorType);
        ctor.visitVarInsn(Opcodes.ALOAD, behaviorLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(bm.propertyName);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, behaviorInternal,
                             "attach", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
    }

    private static void verifyAttachable(Class<?> type) {
        try {
            type.getMethod("attach", Object.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                "type '" + type.getName() + "' used as Behavior must have "
                + "attach(Object, String) method");
        }
    }

    private void emitObjectListAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                          int outerLocal, Ast.ObjectListValue listVal,
                                          TypeRegistry registry,
                                          int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                          Map<String, byte[]> classes, String componentBinaryName,
                                          Map<String, Class<? extends QObject>> idTypes,
                                          Map<String, List<String>> customSignalParams,
                                          String listFieldName) {
        Field listField = findListFieldOrNull(outerType, listFieldName);
        if (listField == null) {
            throw new IllegalArgumentException(
                "no List field '" + listFieldName + "' on " + outerType.getName());
        }
        for (Ast.ObjectNode node : listVal.objects) {
            emitChildObjectInto(ctor, outerType, outerLocal, node, registry,
                                localCounter, bindingCounter, handlerCounter, classes,
                                componentBinaryName, idTypes, customSignalParams, listFieldName);
        }
    }

    private void emitChangeSinkAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                          int outerLocal, String componentBinaryName,
                                          int[] bindingCounter, Map<String, byte[]> classes,
                                          String name, Ast.Expression expr,
                                          Map<String, Class<? extends QObject>> idTypes) {
        if (expr instanceof Ast.LiteralExpr) {
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitLdcInsn(name);
            loadLiteral(ctor, (Ast.LiteralExpr) expr);
            ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SINK_INTERNAL,
                                 "addChange", "(Ljava/lang/String;Ljava/lang/Object;)V", true);
            return;
        }
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');
        int n = bindingCounter[0]++;
        String bindingBinaryName = componentBinaryName + "$Binding$" + n;
        String bindingInternal = bindingBinaryName.replace('.', '/');
        byte[] bindingBytes = emitBindingClass(bindingInternal, outerInternal, outerType, expr,
                                               componentInternal, idTypes);
        classes.put(bindingBinaryName, bindingBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(name);
        ctor.visitTypeInsn(Opcodes.NEW, bindingInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, bindingInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SINK_INTERNAL,
                             "addChangeBinding",
                             "(Ljava/lang/String;L" + BINDING_INTERNAL + ";)V", true);
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
                                       String propName, Ast.Expression expr,
                                       Map<String, Class<? extends QObject>> idTypes) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');

        int n = bindingCounter[0]++;
        String bindingBinaryName = componentBinaryName + "$Binding$" + n;
        String bindingInternal = bindingBinaryName.replace('.', '/');

        byte[] bindingBytes = emitBindingClass(bindingInternal, outerInternal, outerType, expr,
                                               componentInternal, idTypes);
        classes.put(bindingBinaryName, bindingBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, propName, PROPERTY_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, bindingInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, bindingInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "bind", "(L" + BINDING_INTERNAL + ";)V", false);
    }

    private void emitGroupedBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
                                    int outerLocal, String componentBinaryName,
                                    int[] bindingCounter, Map<String, byte[]> classes,
                                    String groupName, String propName, Ast.Expression expr,
                                    Map<String, Class<? extends QObject>> idTypes) {
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
        String componentInternal = componentBinaryName.replace('.', '/');

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
        byte[] bindingBytes = emitBindingClass(bindingInternal, outerInternal, outerType, expr,
                                               componentInternal, idTypes);
        classes.put(bindingBinaryName, bindingBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, groupDeclOwner, groupName,
                            "L" + groupTypeInternal + ";");
        ctor.visitFieldInsn(Opcodes.GETFIELD, propDeclOwner, propName, PROPERTY_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, bindingInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, bindingInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
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
                                         Ast.Expression body,
                                         Map<String, Class<? extends QObject>> idTypes,
                                         List<String> signalParams) {
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');
        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');
        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body,
                                               componentInternal, idTypes,
                                               signalParams != null ? signalParams : Collections.<String>emptyList());
        classes.put(handlerBinaryName, handlerBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, signalOwnerInternal, signalName, SIGNAL_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, handlerInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, handlerInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    private void emitSignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                   int outerLocal, String componentBinaryName,
                                   int[] handlerCounter, Map<String, byte[]> classes,
                                   Field signalField, Ast.Expression body,
                                   Map<String, Class<? extends QObject>> idTypes) {
        String declOwner = Type.getInternalName(signalField.getDeclaringClass());
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');

        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');

        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body,
                                               componentInternal, idTypes,
                                               Collections.<String>emptyList());
        classes.put(handlerBinaryName, handlerBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, signalField.getName(), SIGNAL_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, handlerInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, handlerInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    private byte[] emitHandlerClass(String handlerInternal, String outerInternal,
                                    Class<?> outerType, Ast.Expression body,
                                    String componentInternal,
                                    Map<String, Class<? extends QObject>> idTypes,
                                    List<String> signalParams) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 handlerInternal, null, "java/lang/Object",
                 new String[]{SIGNAL_HANDLER_INTERNAL});

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                      "outer", "L" + outerInternal + ";", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                      "root", "L" + componentInternal + ";", null, null).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                                            "(L" + outerInternal + ";L" + componentInternal + ";)V",
                                            null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, handlerInternal, "outer", "L" + outerInternal + ";");
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 2);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, handlerInternal, "root", "L" + componentInternal + ";");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor invoke = cw.visitMethod(Opcodes.ACC_PUBLIC, "invoke",
                                              "([Ljava/lang/Object;)V", null, null);
        invoke.visitCode();
        Map<String, Integer> paramIdx = new LinkedHashMap<>();
        for (int i = 0; i < signalParams.size(); i++) paramIdx.put(signalParams.get(i), i);
        ExpressionCodegen codegen = new ExpressionCodegen(outerInternal, handlerInternal, outerType,
                                                          componentInternal, idTypes, paramIdx);
        codegen.emit(invoke, body);
        invoke.visitInsn(Opcodes.POP);
        invoke.visitInsn(Opcodes.RETURN);
        invoke.visitMaxs(0, 0);
        invoke.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] emitBindingClass(String bindingInternal, String outerInternal,
                                    Class<?> outerType, Ast.Expression expr,
                                    String componentInternal,
                                    Map<String, Class<? extends QObject>> idTypes) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 bindingInternal, null, BINDING_INTERNAL, null);

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                      "outer", "L" + outerInternal + ";", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                      "root", "L" + componentInternal + ";", null, null).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                                            "(L" + outerInternal + ";L" + componentInternal + ";)V",
                                            null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, BINDING_INTERNAL, "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, bindingInternal, "outer", "L" + outerInternal + ";");
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 2);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, bindingInternal, "root", "L" + componentInternal + ";");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor eval = cw.visitMethod(Opcodes.ACC_PUBLIC, "evaluate",
                                            "()Ljava/lang/Object;", null, null);
        eval.visitCode();
        ExpressionCodegen codegen = new ExpressionCodegen(outerInternal, bindingInternal, outerType,
                                                          componentInternal, idTypes);
        codegen.emit(eval, expr);
        eval.visitInsn(Opcodes.ARETURN);
        eval.visitMaxs(0, 0);
        eval.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String idOf(Ast.ObjectNode obj) {
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.PropertyBinding) {
                Ast.PropertyBinding b = (Ast.PropertyBinding) m;
                if (b.path.size() == 1 && "id".equals(b.path.get(0))) {
                    if (b.value instanceof Ast.ExpressionValue) {
                        Ast.Expression e = ((Ast.ExpressionValue) b.value).expr;
                        if (e instanceof Ast.IdentifierExpr) {
                            return ((Ast.IdentifierExpr) e).name;
                        }
                    }
                    throw new IllegalArgumentException("id value must be a simple identifier");
                }
            }
        }
        return null;
    }

    private static void collectIds(Ast.ObjectNode obj, TypeRegistry registry,
                                   Map<String, Class<? extends QObject>> out) {
        String id = idOf(obj);
        if (id != null) {
            Class<? extends QObject> t = registry.resolve(obj.typeName);
            if (out.put(id, t) != null) {
                throw new IllegalArgumentException("duplicate id: " + id);
            }
        }
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.ChildObject) {
                collectIds(((Ast.ChildObject) m).object, registry, out);
            } else if (m instanceof Ast.PropertyBinding) {
                Ast.Value v = ((Ast.PropertyBinding) m).value;
                if (v instanceof Ast.ObjectValue) {
                    collectIds(((Ast.ObjectValue) v).object, registry, out);
                } else if (v instanceof Ast.ObjectListValue) {
                    for (Ast.ObjectNode n : ((Ast.ObjectListValue) v).objects) {
                        collectIds(n, registry, out);
                    }
                }
            }
        }
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
