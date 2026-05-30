package io.qml4j.compiler.bytecode;

import io.qml4j.compiler.CompiledUnit;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.compiler.bytecode.ExpressionCodegen.AliasRef;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.PropertyChangeSink;
import io.qml4j.engine.QmlDefaultList;
import io.qml4j.engine.SignalRelay;
import io.qml4j.engine.binding.Property;
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
    private static final String QOBJECT_INTERNAL = "io/qml4j/engine/QObject";
    private static final String DELEGATE_FACTORY_INTERNAL = "io/qml4j/engine/DelegateFactory";
    private static final String DELEGATE_HOST_INTERNAL = "io/qml4j/engine/DelegateHost";
    private static final String SIGNAL_RELAY_INTERNAL = "io/qml4j/engine/SignalRelay";

    private static final ThreadLocal<int[]> DELEGATE_SCOPE_DEPTH =
        ThreadLocal.withInitial(() -> new int[]{0});

    private static final ThreadLocal<java.util.Deque<TypeRegistry>> REGISTRY_STACK =
        ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public static boolean inDelegateScope() {
        return DELEGATE_SCOPE_DEPTH.get()[0] > 0;
    }

    private static void enterDelegateScope() {
        DELEGATE_SCOPE_DEPTH.get()[0]++;
    }

    private static void exitDelegateScope() {
        DELEGATE_SCOPE_DEPTH.get()[0]--;
    }

    public static Class<? extends QObject> currentSingletonClass(String name) {
        for (TypeRegistry r : REGISTRY_STACK.get()) {
            Class<? extends QObject> c = r.singletonClass(name);
            if (c != null) return c;
        }
        return null;
    }

    public static TypeRegistry currentRegistry() {
        return REGISTRY_STACK.get().peek();
    }

    public static void tryResolveType(String name) {
        TypeRegistry r = currentRegistry();
        if (r == null) return;
        if (r.isRegistered(name)) return;
        try { r.resolve(name); }
        catch (IllegalArgumentException ignored) {}
    }

    private final AtomicInteger componentCounter = new AtomicInteger();

    private ClassWriter activeComponentCw;
    private int factoryCounter;

    public CompiledUnit compile(Ast.QmlDocument doc, TypeRegistry registry) {
        REGISTRY_STACK.get().push(registry);
        try {
            Class<? extends QObject> rootType = registry.resolve(doc.root.typeName);
            int id = componentCounter.getAndIncrement();
            String componentBinaryName = "io.qml4j.generated.Component$" + id;
            String componentInternal = componentBinaryName.replace('.', '/');
            String rootInternal = Type.getInternalName(rootType);

            Map<String, Class<? extends QObject>> idTypes = new LinkedHashMap<>();
            collectIds(doc.root, registry, idTypes, false);

            Map<String, byte[]> classes = new LinkedHashMap<>();

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                     componentInternal, null, rootInternal, null);
            this.activeComponentCw = cw;
            this.factoryCounter = 0;
            try {
                if (doc.hasPragma("Singleton")) {
                    emitSingletonAccessor(cw, componentInternal);
                }
                return compileBody(doc, registry, rootType, componentBinaryName,
                                   componentInternal, rootInternal, idTypes, classes, cw);
            } finally {
                this.activeComponentCw = null;
            }
        } finally {
            REGISTRY_STACK.get().pop();
        }
    }

    private static void emitSingletonAccessor(ClassWriter cw, String componentInternal) {
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                      "__singleton", "Ljava/lang/Object;", null, null).visitEnd();
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
            "__instance", "()Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, componentInternal, "__singleton",
                          "Ljava/lang/Object;");
        org.objectweb.asm.Label ret = new org.objectweb.asm.Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, ret);
        mv.visitTypeInsn(Opcodes.NEW, componentInternal);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, componentInternal, "<init>", "()V", false);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, componentInternal, "__singleton",
                          "Ljava/lang/Object;");
        mv.visitLabel(ret);
        mv.visitFieldInsn(Opcodes.GETSTATIC, componentInternal, "__singleton",
                          "Ljava/lang/Object;");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private CompiledUnit compileBody(Ast.QmlDocument doc, TypeRegistry registry,
                                     Class<? extends QObject> rootType,
                                     String componentBinaryName, String componentInternal,
                                     String rootInternal,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, byte[]> classes, ClassWriter cw) {
        int[] bindingCounter = {0};
        int[] handlerCounter = {0};
        int[] localCounter = {1};

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

        List<DeclaredProp> rootDecls = collectPropertyDecls(doc.root, rootType);
        Map<String, String> rootDeclaredProps = new LinkedHashMap<>();
        Map<String, AliasRef> rootAliases = new LinkedHashMap<>();
        List<DeclaredProp> rootRegularDecls = new ArrayList<>();
        List<AliasDecl> rootAliasDecls = new ArrayList<>();
        for (DeclaredProp dp : rootDecls) {
            cw.visitField(Opcodes.ACC_PUBLIC, dp.name, PROPERTY_DESC, null, null).visitEnd();
            if ("alias".equals(dp.typeName)) {
                AliasDecl ad = parseAlias(dp);
                rootAliasDecls.add(ad);
                rootAliases.put(ad.name, new AliasRef(ad.targetId, ad.targetProperty));
            } else {
                rootRegularDecls.add(dp);
                rootDeclaredProps.put(dp.name, componentInternal);
            }
        }

        List<Ast.FunctionDeclaration> rootFunctionDecls = new ArrayList<>();
        Map<String, Integer> rootFunctions = new LinkedHashMap<>();
        for (Ast.ObjectMember m : doc.root.members) {
            if (m instanceof Ast.FunctionDeclaration) {
                Ast.FunctionDeclaration fd = (Ast.FunctionDeclaration) m;
                if (rootFunctions.put(fd.name, fd.paramNames.size()) != null) {
                    throw new IllegalArgumentException("duplicate function declaration: " + fd.name);
                }
                if (findPropertyFieldOrNull(rootType, fd.name) != null
                    || findSignalFieldOrNull(rootType, fd.name) != null) {
                    throw new IllegalArgumentException(
                        "function '" + fd.name + "' shadows existing field on " + rootType.getName());
                }
                rootFunctionDecls.add(fd);
            }
        }

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, rootInternal, "<init>", "()V", false);
        ctor.visitMethodInsn(Opcodes.INVOKESTATIC, PROPERTY_INTERNAL,
                             "pushDeferred", "()V", false);
        for (String sig : rootSignalNames) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitTypeInsn(Opcodes.NEW, SIGNAL_INTERNAL);
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, SIGNAL_INTERNAL, "<init>", "()V", false);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, sig, SIGNAL_DESC);
        }
        for (DeclaredProp dp : rootRegularDecls) {
            emitInitDeclaredProperty(ctor, 0, componentInternal, dp);
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
                       componentInternal, rootSignalNames, customSignalParams, idTypes,
                       rootDeclaredProps, rootAliases, rootFunctions);

        for (AliasDecl ad : rootAliasDecls) {
            emitAliasLink(ctor, componentInternal, rootId, rootType,
                          idTypes, rootDeclaredProps, ad);
        }

        ctor.visitMethodInsn(Opcodes.INVOKESTATIC, PROPERTY_INTERNAL,
                             "flushDeferred", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        for (Ast.FunctionDeclaration fd : rootFunctionDecls) {
            emitRootFunctionMethod(cw, componentInternal, rootType, fd,
                                   idTypes, rootDeclaredProps, rootAliases, rootFunctions,
                                   componentBinaryName, bindingCounter, classes);
        }

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
                                Map<String, Class<? extends QObject>> idTypes,
                                Map<String, String> declaredProps,
                                Map<String, AliasRef> aliases,
                                Map<String, Integer> rootFunctions) {
        List<Ast.ObjectMember> deferred = new ArrayList<>();
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.SignalDeclaration) continue;
            if (m instanceof Ast.FunctionDeclaration) {
                if (outerLocal == 0) continue;
                emitChildScopeFunction(ctor, outerType, outerLocal,
                                       (Ast.FunctionDeclaration) m, componentBinaryName,
                                       idTypes, declaredProps, aliases, rootFunctions,
                                       bindingCounter, classes);
                continue;
            }
            if (isStateAssignment(m)) { deferred.add(m); continue; }
            emitMember(ctor, outerType, outerLocal, m, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       customSignalOwner, customSignals, customSignalParams,
                       idTypes, declaredProps, aliases, rootFunctions);
        }
        for (Ast.ObjectMember m : deferred) {
            ctor.visitMethodInsn(Opcodes.INVOKESTATIC, PROPERTY_INTERNAL,
                                 "drainDeferred", "()V", false);
            emitMember(ctor, outerType, outerLocal, m, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       customSignalOwner, customSignals, customSignalParams,
                       idTypes, declaredProps, aliases, rootFunctions);
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
                            Map<String, Class<? extends QObject>> idTypes,
                            Map<String, String> declaredProps,
                            Map<String, AliasRef> aliases,
                            Map<String, Integer> rootFunctions) {
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
                                         customSignalParams, path.get(0), declaredProps, rootFunctions);
                return;
            }
            if (b.value instanceof Ast.ObjectValue) {
                if (path.size() == 2) {
                    emitGroupedObjectAssignment(ctor, outerType, outerLocal,
                                                ((Ast.ObjectValue) b.value).object, registry,
                                                localCounter, bindingCounter, handlerCounter,
                                                classes, componentBinaryName, idTypes,
                                                customSignalParams, path.get(0), path.get(1),
                                                declaredProps, rootFunctions);
                    return;
                }
                if (path.size() != 1) {
                    throw new UnsupportedOperationException(
                        "object assignment to nested path not supported: " + path);
                }
                emitObjectValueAssignment(ctor, outerType, outerLocal,
                                          ((Ast.ObjectValue) b.value).object, registry,
                                          localCounter, bindingCounter, handlerCounter,
                                          classes, componentBinaryName, idTypes,
                                          customSignalParams, path.get(0), declaredProps, rootFunctions);
                return;
            }
            boolean isExprVal = b.value instanceof Ast.ExpressionValue;
            boolean isStmtBlock = b.value instanceof Ast.StatementBlockValue;
            if (!isExprVal && !isStmtBlock) {
                throw new UnsupportedOperationException("only expression/statement bindings supported");
            }
            if (path.size() == 1) {
                String key = path.get(0);
                String signalName = signalNameFromHandler(key);
                boolean isCustomHandler = signalName != null && customSignals.contains(signalName);
                Field signalField = (signalName != null && !isCustomHandler)
                    ? findSignalFieldOrNull(outerType, signalName) : null;
                boolean isRelay = signalName != null && !isCustomHandler && signalField == null
                    && SignalRelay.class.isAssignableFrom(outerType);
                boolean isHandler = isCustomHandler || signalField != null || isRelay;
                if (isStmtBlock && !isHandler) {
                    throw new UnsupportedOperationException(
                        "statement block only allowed as signal handler body: " + key);
                }
                if (isHandler) {
                    Ast.Statement handlerBody = toStatement(b.value);
                    if (isCustomHandler) {
                        emitCustomSignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                                handlerCounter, bindingCounter, classes,
                                                customSignalOwner, signalName, handlerBody, idTypes,
                                                customSignalParams.get(signalName), declaredProps, aliases,
                                                rootFunctions);
                    } else if (isRelay) {
                        emitRelaySignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                               handlerCounter, bindingCounter, classes, signalName, handlerBody, idTypes,
                                               declaredProps, aliases, rootFunctions);
                    } else {
                        emitSignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                          handlerCounter, bindingCounter, classes, signalField, handlerBody, idTypes,
                                          declaredProps, aliases, rootFunctions);
                    }
                    return;
                }
                Ast.Expression e = ((Ast.ExpressionValue) b.value).expr;
                if (PropertyChangeSink.class.isAssignableFrom(outerType) && !"target".equals(key)) {
                    emitChangeSinkAssignment(ctor, outerType, outerLocal, componentBinaryName,
                                             bindingCounter, classes, key, e, idTypes,
                                             declaredProps, aliases, rootFunctions);
                    return;
                }
                if (e instanceof Ast.LiteralExpr) {
                    emitLiteralAssignment(ctor, outerType, outerLocal, key, (Ast.LiteralExpr) e);
                } else {
                    emitExpressionBinding(ctor, outerType, outerLocal, componentBinaryName,
                                          bindingCounter, classes, key, e, idTypes,
                                          declaredProps, aliases, rootFunctions);
                }
                return;
            }
            if (path.size() == 2 && "Keys".equals(path.get(0))) {
                String signalName = signalNameFromHandler(path.get(1));
                if (signalName == null) {
                    throw new UnsupportedOperationException(
                        "Keys attached property supports only on<Signal> handlers: " + path);
                }
                Ast.Statement handlerBody = toStatement(b.value);
                emitKeysHandler(ctor, outerType, outerLocal, componentBinaryName,
                                handlerCounter, bindingCounter, classes, signalName, handlerBody,
                                idTypes, declaredProps, aliases, rootFunctions);
                return;
            }
            if (path.size() == 2) {
                if (!isExprVal) {
                    throw new UnsupportedOperationException(
                        "statement block not allowed in grouped binding: " + path);
                }
                Ast.Expression e = ((Ast.ExpressionValue) b.value).expr;
                emitGroupedBinding(ctor, outerType, outerLocal, componentBinaryName,
                                   bindingCounter, classes, path.get(0), path.get(1), e, idTypes,
                                   declaredProps, aliases, rootFunctions);
                return;
            }
            throw new UnsupportedOperationException("nested grouped property path not supported: " + path);
        }
        if (m instanceof Ast.ChildObject) {
            emitChildObject(ctor, outerType, outerLocal, ((Ast.ChildObject) m).object, registry,
                            localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                            idTypes, customSignalParams, declaredProps, rootFunctions);
            return;
        }
        if (m instanceof Ast.BehaviorMember) {
            emitBehaviorMember(ctor, outerType, outerLocal, (Ast.BehaviorMember) m, registry,
                               localCounter, bindingCounter, handlerCounter, classes,
                               componentBinaryName, idTypes, customSignalParams, declaredProps, rootFunctions);
            return;
        }
        if (m instanceof Ast.SignalDeclaration) {
            throw new IllegalStateException("signal declaration should be handled at object scope");
        }
        if (m instanceof Ast.PropertyDeclaration) {
            emitPropertyDeclarationInitializer(ctor, outerType, outerLocal, componentBinaryName,
                                               bindingCounter, classes, (Ast.PropertyDeclaration) m,
                                               idTypes, declaredProps, aliases, rootFunctions);
            return;
        }
        if (m instanceof Ast.FunctionDeclaration) {
            throw new IllegalStateException(
                "function declaration should have been handled by emitObjectBody");
        }
        throw new IllegalStateException("unknown member: " + m.getClass());
    }

    private void emitPropertyDeclarationInitializer(MethodVisitor ctor, Class<? extends QObject> outerType,
                                                    int outerLocal, String componentBinaryName,
                                                    int[] bindingCounter, Map<String, byte[]> classes,
                                                    Ast.PropertyDeclaration pd,
                                                    Map<String, Class<? extends QObject>> idTypes,
                                                    Map<String, String> declaredProps,
                                                    Map<String, AliasRef> aliases,
                                                    Map<String, Integer> rootFunctions) {
        if ("alias".equals(pd.typeName)) return;
        if (pd.initializer == null) return;
        if (!(pd.initializer instanceof Ast.ExpressionValue)) {
            throw new UnsupportedOperationException(
                "only expression initializer supported for property: " + pd.name);
        }
        String ownerInternal = declaredProps.get(pd.name);
        if (ownerInternal == null) {
            throw new IllegalStateException("declared property not registered: " + pd.name);
        }
        Ast.Expression e = ((Ast.ExpressionValue) pd.initializer).expr;
        if (e instanceof Ast.LiteralExpr) {
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, ownerInternal, pd.name, PROPERTY_DESC);
            loadLiteral(ctor, (Ast.LiteralExpr) e);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                 "set", "(Ljava/lang/Object;)V", false);
            return;
        }
        emitDeclaredPropertyBinding(ctor, outerType, outerLocal, componentBinaryName,
                                    bindingCounter, classes, ownerInternal, pd.name, e,
                                    idTypes, declaredProps, aliases, rootFunctions);
    }

    private void emitDeclaredPropertyBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
                                             int outerLocal, String componentBinaryName,
                                             int[] bindingCounter, Map<String, byte[]> classes,
                                             String ownerInternal, String name, Ast.Expression expr,
                                             Map<String, Class<? extends QObject>> idTypes,
                                             Map<String, String> declaredProps,
                                             Map<String, AliasRef> aliases,
                                             Map<String, Integer> rootFunctions) {
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');
        String bindingInternal = emitBindingClass(outerInternal, outerType, expr, componentBinaryName,
                                                  idTypes, declaredProps, aliases, rootFunctions,
                                                  bindingCounter, classes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, ownerInternal, name, PROPERTY_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, bindingInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, bindingInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "bind", "(L" + BINDING_INTERNAL + ";)V", false);
    }

    private void emitChildObject(MethodVisitor ctor, Class<? extends QObject> outerType,
                                 int outerLocal, Ast.ObjectNode child, TypeRegistry registry,
                                 int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                 Map<String, byte[]> classes, String componentBinaryName,
                                 Map<String, Class<? extends QObject>> idTypes,
                                 Map<String, List<String>> outerSignalParams,
                                 Map<String, String> declaredProps,
                                 Map<String, Integer> rootFunctions) {
        emitChildObjectInto(ctor, outerType, outerLocal, child, registry,
                            localCounter, bindingCounter, handlerCounter, classes,
                            componentBinaryName, idTypes, outerSignalParams,
                            defaultListFieldFor(outerType), declaredProps, rootFunctions);
    }

    private static String defaultListFieldFor(Class<?> type) {
        QmlDefaultList ann = type.getAnnotation(QmlDefaultList.class);
        return ann != null ? ann.value() : "children";
    }

    private void emitChildObjectInto(MethodVisitor ctor, Class<? extends QObject> outerType,
                                     int outerLocal, Ast.ObjectNode child, TypeRegistry registry,
                                     int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                     Map<String, byte[]> classes, String componentBinaryName,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, List<String>> outerSignalParams,
                                     String listFieldName,
                                     Map<String, String> declaredProps,
                                     Map<String, Integer> rootFunctions) {
        if (registry.isSingleton(child.typeName)) {
            throw new IllegalArgumentException(
                "cannot instantiate singleton type with object syntax: " + child.typeName);
        }
        Class<? extends QObject> childType = registry.resolve(child.typeName);
        String parentInternal = Type.getInternalName(childType);
        String componentInternal = componentBinaryName.replace('.', '/');

        Ast.ObjectNode hostNode = child;
        String delegateFactoryBinaryName = null;
        if (DelegateHost.class.isAssignableFrom(childType)) {
            Ast.ObjectNode delegateNode = null;
            List<Ast.ObjectMember> hostMembers = new ArrayList<>();
            for (Ast.ObjectMember m : child.members) {
                if (m instanceof Ast.ChildObject) {
                    if (delegateNode != null) {
                        throw new IllegalArgumentException(
                            child.typeName + " must declare exactly one delegate child object");
                    }
                    delegateNode = ((Ast.ChildObject) m).object;
                } else {
                    hostMembers.add(m);
                }
            }
            if (delegateNode == null) {
                throw new IllegalArgumentException(
                    child.typeName + " requires a delegate child object");
            }
            hostNode = new Ast.ObjectNode(child.typeName, hostMembers);
            delegateFactoryBinaryName = emitDelegateFactory(delegateNode, registry,
                                                            bindingCounter, handlerCounter,
                                                            classes, componentBinaryName,
                                                            idTypes, rootFunctions);
        }

        Set<String> childSignals = new LinkedHashSet<>();
        Map<String, List<String>> childSignalParams = new LinkedHashMap<>();
        for (Ast.ObjectMember m : hostNode.members) {
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

        List<DeclaredProp> childDecls = collectPropertyDecls(hostNode, childType);

        String childInternal;
        String childSignalOwner;
        Map<String, String> childDeclaredProps = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : declaredProps.entrySet()) {
            if (componentInternal.equals(e.getValue())) {
                childDeclaredProps.put(e.getKey(), e.getValue());
            }
        }
        if (childSignals.isEmpty() && childDecls.isEmpty()) {
            childInternal = parentInternal;
            childSignalOwner = null;
        } else {
            String childSubBinaryName = componentBinaryName + "$Child$" + localCounter[0];
            childInternal = childSubBinaryName.replace('.', '/');
            byte[] subBytes = emitChildSubclass(childInternal, parentInternal, childSignals, childDecls);
            classes.put(childSubBinaryName, subBytes);
            childSignalOwner = childInternal;
            for (DeclaredProp dp : childDecls) {
                childDeclaredProps.put(dp.name, childInternal);
            }
        }

        int childLocal = localCounter[0]++;

        ctor.visitTypeInsn(Opcodes.NEW, childInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, childInternal, "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ASTORE, childLocal);

        ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, QOBJECT_INTERNAL,
                             "__setQmlParent", "(L" + QOBJECT_INTERNAL + ";)V", false);

        for (String sig : childSignals) {
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitTypeInsn(Opcodes.NEW, SIGNAL_INTERNAL);
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, SIGNAL_INTERNAL, "<init>", "()V", false);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, childInternal, sig, SIGNAL_DESC);
        }
        for (DeclaredProp dp : childDecls) {
            emitInitDeclaredProperty(ctor, childLocal, childInternal, dp);
        }

        String childId = idOf(hostNode);
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
        if (listField != null && listAcceptsElement(listField, childType)) {
            String declOwner = Type.getInternalName(listField.getDeclaringClass());
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, listFieldName, LIST_DESC);
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_INTERNAL,
                                 "add", "(Ljava/lang/Object;)Z", true);
            ctor.visitInsn(Opcodes.POP);
        }

        emitObjectBody(ctor, childType, childLocal, hostNode, registry,
                       localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                       childSignalOwner, childSignals, childSignalParams, idTypes,
                       childDeclaredProps, Collections.<String, AliasRef>emptyMap(), rootFunctions);

        if (delegateFactoryBinaryName != null) {
            String factoryInternal = delegateFactoryBinaryName.replace('.', '/');
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitTypeInsn(Opcodes.NEW, factoryInternal);
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, factoryInternal, "<init>",
                                 "(L" + componentInternal + ";)V", false);
            ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DELEGATE_HOST_INTERNAL,
                                 "setDelegate",
                                 "(L" + DELEGATE_FACTORY_INTERNAL + ";)V", true);
        }
    }

    private byte[] emitChildSubclass(String subInternal, String parentInternal,
                                     Set<String> signalNames, List<DeclaredProp> propDecls) {
        return emitChildSubclass(subInternal, parentInternal, signalNames, propDecls, null);
    }

    private byte[] emitChildSubclass(String subInternal, String parentInternal,
                                     Set<String> signalNames, List<DeclaredProp> propDecls,
                                     String[] extraInterfaces) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 subInternal, null, parentInternal, extraInterfaces);
        for (String sig : signalNames) {
            cw.visitField(Opcodes.ACC_PUBLIC, sig, SIGNAL_DESC, null, null).visitEnd();
        }
        for (DeclaredProp dp : propDecls) {
            cw.visitField(Opcodes.ACC_PUBLIC, dp.name, PROPERTY_DESC, null, null).visitEnd();
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

    private String emitDelegateFactory(Ast.ObjectNode delegateNode, TypeRegistry registry,
                                       int[] bindingCounter, int[] handlerCounter,
                                       Map<String, byte[]> classes, String componentBinaryName,
                                       Map<String, Class<? extends QObject>> idTypes,
                                       Map<String, Integer> rootFunctions) {
        int n = factoryCounter++;
        Class<? extends QObject> delType = registry.resolve(delegateNode.typeName);
        String delBaseInternal = Type.getInternalName(delType);
        String componentInternal = componentBinaryName.replace('.', '/');

        Set<String> delSignals = new LinkedHashSet<>();
        Map<String, List<String>> delSignalParams = new LinkedHashMap<>();
        for (Ast.ObjectMember m : delegateNode.members) {
            if (m instanceof Ast.SignalDeclaration) {
                Ast.SignalDeclaration sd = (Ast.SignalDeclaration) m;
                if (!delSignals.add(sd.name)) {
                    throw new IllegalArgumentException("duplicate signal: " + sd.name);
                }
                if (findSignalFieldOrNull(delType, sd.name) != null) {
                    throw new IllegalArgumentException(
                        "signal '" + sd.name + "' shadows existing field on " + delType.getName());
                }
                delSignalParams.put(sd.name, sd.paramNames);
            }
        }

        List<DeclaredProp> userDecls = collectPropertyDecls(delegateNode, delType);
        for (DeclaredProp dp : userDecls) {
            if ("index".equals(dp.name) || "modelData".equals(dp.name)) {
                throw new IllegalArgumentException(
                    "delegate cannot redeclare reserved property: " + dp.name);
            }
        }
        List<DeclaredProp> fullDecls = new ArrayList<>();
        fullDecls.add(new DeclaredProp("index", "int", null));
        fullDecls.add(new DeclaredProp("modelData", "var", null));
        fullDecls.addAll(userDecls);

        String delegateBinaryName = componentBinaryName + "$Delegate$" + n;
        String delegateInternal = delegateBinaryName.replace('.', '/');
        byte[] subBytes = emitChildSubclass(delegateInternal, delBaseInternal, delSignals,
                                            fullDecls, new String[]{"io/qml4j/engine/DelegateRoot"});
        classes.put(delegateBinaryName, subBytes);

        Map<String, String> delDeclaredProps = new LinkedHashMap<>();
        for (DeclaredProp dp : fullDecls) {
            delDeclaredProps.put(dp.name, delegateInternal);
        }

        emitDelegateMethod(n, delegateNode, registry, bindingCounter, handlerCounter,
                           classes, componentBinaryName, componentInternal,
                           delType, delegateInternal, delSignals, delSignalParams,
                           fullDecls, delDeclaredProps, idTypes, rootFunctions);

        String factoryBinaryName = componentBinaryName + "$Factory$" + n;
        byte[] factoryBytes = emitDelegateFactoryClass(factoryBinaryName, componentInternal, n);
        classes.put(factoryBinaryName, factoryBytes);
        return factoryBinaryName;
    }

    private void emitDelegateMethod(int n, Ast.ObjectNode delegateNode, TypeRegistry registry,
                                    int[] bindingCounter, int[] handlerCounter,
                                    Map<String, byte[]> classes, String componentBinaryName,
                                    String componentInternal,
                                    Class<? extends QObject> delType, String delegateInternal,
                                    Set<String> delSignals,
                                    Map<String, List<String>> delSignalParams,
                                    List<DeclaredProp> fullDecls,
                                    Map<String, String> delDeclaredProps,
                                    Map<String, Class<? extends QObject>> idTypes,
                                    Map<String, Integer> rootFunctions) {
        MethodVisitor mv = activeComponentCw.visitMethod(Opcodes.ACC_PUBLIC,
            "_delegate$" + n,
            "(ILjava/lang/Object;)L" + QOBJECT_INTERNAL + ";", null, null);
        mv.visitCode();

        int delegateLocal = 3;
        int[] localCounter = {4};

        mv.visitMethodInsn(Opcodes.INVOKESTATIC, PROPERTY_INTERNAL,
                           "pushDeferred", "()V", false);

        mv.visitTypeInsn(Opcodes.NEW, delegateInternal);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, delegateInternal, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, delegateLocal);

        for (String sig : delSignals) {
            mv.visitVarInsn(Opcodes.ALOAD, delegateLocal);
            mv.visitTypeInsn(Opcodes.NEW, SIGNAL_INTERNAL);
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, SIGNAL_INTERNAL, "<init>", "()V", false);
            mv.visitFieldInsn(Opcodes.PUTFIELD, delegateInternal, sig, SIGNAL_DESC);
        }
        for (DeclaredProp dp : fullDecls) {
            emitInitDeclaredProperty(mv, delegateLocal, delegateInternal, dp);
        }

        mv.visitVarInsn(Opcodes.ALOAD, delegateLocal);
        mv.visitFieldInsn(Opcodes.GETFIELD, delegateInternal, "index", PROPERTY_DESC);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitInsn(Opcodes.I2L);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long",
                           "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                           "set", "(Ljava/lang/Object;)V", false);

        mv.visitVarInsn(Opcodes.ALOAD, delegateLocal);
        mv.visitFieldInsn(Opcodes.GETFIELD, delegateInternal, "modelData", PROPERTY_DESC);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                           "set", "(Ljava/lang/Object;)V", false);

        enterDelegateScope();
        try {
            emitObjectBody(mv, delType, delegateLocal, delegateNode, registry,
                           localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                           delegateInternal, delSignals, delSignalParams, idTypes,
                           delDeclaredProps, Collections.<String, AliasRef>emptyMap(), rootFunctions);
        } finally {
            exitDelegateScope();
        }

        mv.visitMethodInsn(Opcodes.INVOKESTATIC, PROPERTY_INTERNAL,
                           "flushDeferred", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, delegateLocal);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private byte[] emitDelegateFactoryClass(String factoryBinaryName, String componentInternal,
                                            int n) {
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
            "(ILjava/lang/Object;)L" + QOBJECT_INTERNAL + ";", null, null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 0);
        create.visitFieldInsn(Opcodes.GETFIELD, factoryInternal, "root",
                              "L" + componentInternal + ";");
        create.visitVarInsn(Opcodes.ILOAD, 1);
        create.visitVarInsn(Opcodes.ALOAD, 2);
        create.visitMethodInsn(Opcodes.INVOKEVIRTUAL, componentInternal, "_delegate$" + n,
                               "(ILjava/lang/Object;)L" + QOBJECT_INTERNAL + ";", false);
        create.visitInsn(Opcodes.ARETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void emitBehaviorMember(MethodVisitor ctor, Class<? extends QObject> outerType,
                                    int outerLocal, Ast.BehaviorMember bm, TypeRegistry registry,
                                    int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                    Map<String, byte[]> classes, String componentBinaryName,
                                    Map<String, Class<? extends QObject>> idTypes,
                                    Map<String, List<String>> outerSignalParams,
                                    Map<String, String> declaredProps,
                                    Map<String, Integer> rootFunctions) {
        Class<? extends QObject> behaviorType = registry.resolve(bm.typeName);
        verifyAttachable(behaviorType);
        Ast.ObjectNode synth = new Ast.ObjectNode(bm.typeName, bm.members);
        int behaviorLocal = localCounter[0];
        emitChildObjectInto(ctor, outerType, outerLocal, synth, registry,
                            localCounter, bindingCounter, handlerCounter, classes,
                            componentBinaryName, idTypes, outerSignalParams, "children", declaredProps, rootFunctions);

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

    private void emitObjectValueAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                           int outerLocal, Ast.ObjectNode node,
                                           TypeRegistry registry,
                                           int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                           Map<String, byte[]> classes, String componentBinaryName,
                                           Map<String, Class<? extends QObject>> idTypes,
                                           Map<String, List<String>> customSignalParams,
                                           String propName,
                                           Map<String, String> declaredProps,
                                           Map<String, Integer> rootFunctions) {
        Field propField = findPropertyField(outerType, propName);
        String propOwner = Type.getInternalName(propField.getDeclaringClass());
        int childLocal = localCounter[0];
        emitChildObjectInto(ctor, outerType, outerLocal, node, registry,
                            localCounter, bindingCounter, handlerCounter, classes,
                            componentBinaryName, idTypes, customSignalParams, "",
                            declaredProps, rootFunctions);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, propOwner, propName, PROPERTY_DESC);
        ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "set", "(Ljava/lang/Object;)V", false);
    }

    private void emitGroupedObjectAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                             int outerLocal, Ast.ObjectNode node,
                                             TypeRegistry registry,
                                             int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                             Map<String, byte[]> classes, String componentBinaryName,
                                             Map<String, Class<? extends QObject>> idTypes,
                                             Map<String, List<String>> customSignalParams,
                                             String groupName, String propName,
                                             Map<String, String> declaredProps,
                                             Map<String, Integer> rootFunctions) {
        Field groupField;
        try {
            groupField = outerType.getField(groupName);
        } catch (NoSuchFieldException ex) {
            throw new IllegalArgumentException(
                "no group field '" + groupName + "' on " + outerType.getName());
        }
        Class<?> groupType = groupField.getType();
        Field propField = findPropertyField(groupType, propName);
        String groupDeclOwner = Type.getInternalName(groupField.getDeclaringClass());
        String groupTypeInternal = Type.getInternalName(groupType);
        String propDeclOwner = Type.getInternalName(propField.getDeclaringClass());

        int childLocal = localCounter[0];
        emitChildObjectInto(ctor, outerType, outerLocal, node, registry,
                            localCounter, bindingCounter, handlerCounter, classes,
                            componentBinaryName, idTypes, customSignalParams, "",
                            declaredProps, rootFunctions);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, groupDeclOwner, groupName,
                            "L" + groupTypeInternal + ";");
        ctor.visitFieldInsn(Opcodes.GETFIELD, propDeclOwner, propName, PROPERTY_DESC);
        ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "set", "(Ljava/lang/Object;)V", false);
    }

    private void emitObjectListAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                          int outerLocal, Ast.ObjectListValue listVal,
                                          TypeRegistry registry,
                                          int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                                          Map<String, byte[]> classes, String componentBinaryName,
                                          Map<String, Class<? extends QObject>> idTypes,
                                          Map<String, List<String>> customSignalParams,
                                          String listFieldName,
                                          Map<String, String> declaredProps,
                                          Map<String, Integer> rootFunctions) {
        Field listField = findListFieldOrNull(outerType, listFieldName);
        if (listField == null) {
            throw new IllegalArgumentException(
                "no List field '" + listFieldName + "' on " + outerType.getName());
        }
        for (Ast.ObjectNode node : listVal.objects) {
            emitChildObjectInto(ctor, outerType, outerLocal, node, registry,
                                localCounter, bindingCounter, handlerCounter, classes,
                                componentBinaryName, idTypes, customSignalParams, listFieldName,
                                declaredProps, rootFunctions);
        }
    }

    private void emitChangeSinkAssignment(MethodVisitor ctor, Class<? extends QObject> outerType,
                                          int outerLocal, String componentBinaryName,
                                          int[] bindingCounter, Map<String, byte[]> classes,
                                          String name, Ast.Expression expr,
                                          Map<String, Class<? extends QObject>> idTypes,
                                          Map<String, String> declaredProps,
                                          Map<String, AliasRef> aliases,
                                          Map<String, Integer> rootFunctions) {
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
        String bindingInternal = emitBindingClass(outerInternal, outerType, expr, componentBinaryName,
                                                  idTypes, declaredProps, aliases, rootFunctions,
                                                  bindingCounter, classes);

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

    private static boolean listAcceptsElement(Field listField, Class<?> childType) {
        java.lang.reflect.Type gt = listField.getGenericType();
        if (!(gt instanceof java.lang.reflect.ParameterizedType)) return true;
        java.lang.reflect.Type[] args = ((java.lang.reflect.ParameterizedType) gt).getActualTypeArguments();
        if (args.length == 0) return true;
        java.lang.reflect.Type arg = args[0];
        Class<?> elemClass;
        if (arg instanceof Class) {
            elemClass = (Class<?>) arg;
        } else if (arg instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.Type raw = ((java.lang.reflect.ParameterizedType) arg).getRawType();
            elemClass = (raw instanceof Class) ? (Class<?>) raw : Object.class;
        } else {
            return true;
        }
        return elemClass.isAssignableFrom(childType);
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
                                       Map<String, Class<? extends QObject>> idTypes,
                                       Map<String, String> declaredProps,
                                       Map<String, AliasRef> aliases,
                                       Map<String, Integer> rootFunctions) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');

        String bindingInternal = emitBindingClass(outerInternal, outerType, expr, componentBinaryName,
                                                  idTypes, declaredProps, aliases, rootFunctions,
                                                  bindingCounter, classes);

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
                                    Map<String, Class<? extends QObject>> idTypes,
                                    Map<String, String> declaredProps,
                                    Map<String, AliasRef> aliases,
                                    Map<String, Integer> rootFunctions) {
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

        String bindingInternal = emitBindingClass(outerInternal, outerType, expr, componentBinaryName,
                                                  idTypes, declaredProps, aliases, rootFunctions,
                                                  bindingCounter, classes);

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

    private void emitKeysHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                 int outerLocal, String componentBinaryName,
                                 int[] handlerCounter, int[] bindingCounter, Map<String, byte[]> classes,
                                 String signalName, Ast.Statement body,
                                 Map<String, Class<? extends QObject>> idTypes,
                                 Map<String, String> declaredProps,
                                 Map<String, AliasRef> aliases,
                                 Map<String, Integer> rootFunctions) {
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

        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');
        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body,
                                               componentInternal, componentBinaryName, idTypes,
                                               Collections.singletonList("event"),
                                               declaredProps, aliases, rootFunctions,
                                               bindingCounter, classes);
        classes.put(handlerBinaryName, handlerBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, keysOwnerInternal, "keys",
                             "()L" + keysTypeInternal + ";", false);
        ctor.visitFieldInsn(Opcodes.GETFIELD, keysTypeInternal, signalName, SIGNAL_DESC);
        ctor.visitTypeInsn(Opcodes.NEW, handlerInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, handlerInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
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

    private static Ast.Statement toStatement(Ast.Value v) {
        if (v instanceof Ast.StatementBlockValue) {
            return ((Ast.StatementBlockValue) v).block;
        }
        Ast.Expression expr = ((Ast.ExpressionValue) v).expr;
        return new Ast.Block(Collections.<Ast.Statement>singletonList(new Ast.ExprStmt(expr)));
    }

    private void emitCustomSignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                         int outerLocal, String componentBinaryName,
                                         int[] handlerCounter, int[] bindingCounter, Map<String, byte[]> classes,
                                         String signalOwnerInternal, String signalName,
                                         Ast.Statement body,
                                         Map<String, Class<? extends QObject>> idTypes,
                                         List<String> signalParams,
                                         Map<String, String> declaredProps,
                                         Map<String, AliasRef> aliases,
                                         Map<String, Integer> rootFunctions) {
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');
        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');
        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body,
                                               componentInternal, componentBinaryName, idTypes,
                                               signalParams != null ? signalParams : Collections.<String>emptyList(),
                                               declaredProps, aliases, rootFunctions,
                                               bindingCounter, classes);
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
                                   int[] handlerCounter, int[] bindingCounter, Map<String, byte[]> classes,
                                   Field signalField, Ast.Statement body,
                                   Map<String, Class<? extends QObject>> idTypes,
                                   Map<String, String> declaredProps,
                                   Map<String, AliasRef> aliases,
                                   Map<String, Integer> rootFunctions) {
        String declOwner = Type.getInternalName(signalField.getDeclaringClass());
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');

        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');

        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body,
                                               componentInternal, componentBinaryName, idTypes,
                                               Collections.<String>emptyList(),
                                               declaredProps, aliases, rootFunctions,
                                               bindingCounter, classes);
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

    private void emitRelaySignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                        int outerLocal, String componentBinaryName,
                                        int[] handlerCounter, int[] bindingCounter, Map<String, byte[]> classes,
                                        String signalName, Ast.Statement body,
                                        Map<String, Class<? extends QObject>> idTypes,
                                        Map<String, String> declaredProps,
                                        Map<String, AliasRef> aliases,
                                        Map<String, Integer> rootFunctions) {
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');

        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');

        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body,
                                               componentInternal, componentBinaryName, idTypes,
                                               Collections.<String>emptyList(),
                                               declaredProps, aliases, rootFunctions,
                                               bindingCounter, classes);
        classes.put(handlerBinaryName, handlerBytes);

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(signalName);
        ctor.visitTypeInsn(Opcodes.NEW, handlerInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, handlerInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SIGNAL_RELAY_INTERNAL,
                             "connectSignal",
                             "(Ljava/lang/String;" + SIGNAL_HANDLER_DESC + ")V", true);
    }

    private byte[] emitHandlerClass(String handlerInternal, String outerInternal,
                                    Class<?> outerType, Ast.Statement body,
                                    String componentInternal,
                                    String componentBinaryName,
                                    Map<String, Class<? extends QObject>> idTypes,
                                    List<String> signalParams,
                                    Map<String, String> declaredProps,
                                    Map<String, AliasRef> aliases,
                                    Map<String, Integer> rootFunctions,
                                    int[] bindingCounter,
                                    Map<String, byte[]> classes) {
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
        Map<String, Integer> localVars = new LinkedHashMap<>();
        ExpressionCodegen codegen = new ExpressionCodegen(outerInternal, handlerInternal, outerType,
                                                          componentInternal, idTypes,
                                                          paramIdx, localVars, declaredProps, aliases,
                                                          rootFunctions);
        @SuppressWarnings("unchecked")
        Class<? extends QObject> outerQ = (Class<? extends QObject>) outerType;
        codegen.setBindingEmitter(childExpr -> emitBindingClass(
            outerInternal, outerQ, childExpr, componentBinaryName,
            idTypes, declaredProps, aliases, rootFunctions, bindingCounter, classes));
        codegen.setArrowEmitter(childArrow -> emitArrowClass(
            outerInternal, outerType, childArrow, componentBinaryName,
            idTypes, declaredProps, aliases, rootFunctions, bindingCounter, classes));
        StatementCodegen stmts = new StatementCodegen(codegen, 2);
        stmts.emit(invoke, body);
        invoke.visitInsn(Opcodes.RETURN);
        invoke.visitMaxs(0, 0);
        invoke.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private String emitBindingClass(String outerInternal,
                                    Class<?> outerType, Ast.Expression expr,
                                    String componentBinaryName,
                                    Map<String, Class<? extends QObject>> idTypes,
                                    Map<String, String> declaredProps,
                                    Map<String, AliasRef> aliases,
                                    Map<String, Integer> rootFunctions,
                                    int[] bindingCounter,
                                    Map<String, byte[]> classes) {
        String componentInternal = componentBinaryName.replace('.', '/');
        int n = bindingCounter[0]++;
        String bindingBinaryName = componentBinaryName + "$Binding$" + n;
        String bindingInternal = bindingBinaryName.replace('.', '/');

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
        ExpressionCodegen codegen = ExpressionCodegen.forBinding(outerInternal, bindingInternal, outerType,
                                                                 componentInternal, idTypes, declaredProps, aliases,
                                                                 rootFunctions);
        codegen.setBindingEmitter(childExpr -> emitBindingClass(
            outerInternal, outerType, childExpr, componentBinaryName,
            idTypes, declaredProps, aliases, rootFunctions, bindingCounter, classes));
        codegen.setArrowEmitter(childArrow -> emitArrowClass(
            outerInternal, outerType, childArrow, componentBinaryName,
            idTypes, declaredProps, aliases, rootFunctions, bindingCounter, classes));
        codegen.emit(eval, expr);
        eval.visitInsn(Opcodes.ARETURN);
        eval.visitMaxs(0, 0);
        eval.visitEnd();

        cw.visitEnd();
        classes.put(bindingBinaryName, cw.toByteArray());
        return bindingInternal;
    }

    private void emitChildScopeFunction(MethodVisitor ctor, Class<? extends QObject> outerType,
                                        int outerLocal, Ast.FunctionDeclaration fd,
                                        String componentBinaryName,
                                        Map<String, Class<? extends QObject>> idTypes,
                                        Map<String, String> declaredProps,
                                        Map<String, AliasRef> aliases,
                                        Map<String, Integer> rootFunctions,
                                        int[] bindingCounter,
                                        Map<String, byte[]> classes) {
        Ast.ArrowFunctionExpr arrowEquiv =
            new Ast.ArrowFunctionExpr(fd.paramNames, null, fd.body);
        String outerInternal = Type.getInternalName(outerType);
        String componentInternal = componentBinaryName.replace('.', '/');
        String funcInternal = emitArrowClass(outerInternal, outerType, arrowEquiv,
                                             componentBinaryName, idTypes, declaredProps,
                                             aliases, rootFunctions, bindingCounter, classes);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(fd.name);
        ctor.visitTypeInsn(Opcodes.NEW, funcInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, funcInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, QOBJECT_INTERNAL,
                             "__putFunction",
                             "(Ljava/lang/String;Lio/qml4j/engine/Callable;)V", false);
    }

    private String emitArrowClass(String outerInternal, Class<?> outerType,
                                  Ast.ArrowFunctionExpr fn,
                                  String componentBinaryName,
                                  Map<String, Class<? extends QObject>> idTypes,
                                  Map<String, String> declaredProps,
                                  Map<String, AliasRef> aliases,
                                  Map<String, Integer> rootFunctions,
                                  int[] bindingCounter,
                                  Map<String, byte[]> classes) {
        String componentInternal = componentBinaryName.replace('.', '/');
        int n = bindingCounter[0]++;
        String arrowBinary = componentBinaryName + "$Arrow$" + n;
        String arrowInternal = arrowBinary.replace('.', '/');

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 arrowInternal, null, "java/lang/Object",
                 new String[]{"io/qml4j/engine/Callable"});

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
        ctor.visitFieldInsn(Opcodes.PUTFIELD, arrowInternal, "outer", "L" + outerInternal + ";");
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 2);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, arrowInternal, "root", "L" + componentInternal + ";");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor call = cw.visitMethod(Opcodes.ACC_PUBLIC, "call",
                                            "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        call.visitCode();

        int firstParamSlot = 2;
        Map<String, Integer> directParams = new LinkedHashMap<>();
        for (int i = 0; i < fn.paramNames.size(); i++) {
            int slot = firstParamSlot + i;
            call.visitVarInsn(Opcodes.ALOAD, 1);
            pushSmallInt(call, i);
            call.visitInsn(Opcodes.AALOAD);
            call.visitVarInsn(Opcodes.ASTORE, slot);
            directParams.put(fn.paramNames.get(i), slot);
        }

        ExpressionCodegen codegen = ExpressionCodegen.forArrow(outerInternal, arrowInternal, outerType,
                                                               componentInternal, idTypes, directParams,
                                                               declaredProps, aliases, rootFunctions);
        @SuppressWarnings("unchecked")
        Class<? extends QObject> outerQ = (Class<? extends QObject>) outerType;
        codegen.setBindingEmitter(childExpr -> emitBindingClass(
            outerInternal, outerQ, childExpr, componentBinaryName,
            idTypes, declaredProps, aliases, rootFunctions, bindingCounter, classes));
        codegen.setArrowEmitter(childArrow -> emitArrowClass(
            outerInternal, outerType, childArrow, componentBinaryName,
            idTypes, declaredProps, aliases, rootFunctions, bindingCounter, classes));

        if (fn.bodyExpr != null) {
            codegen.emit(call, fn.bodyExpr);
            call.visitInsn(Opcodes.ARETURN);
        } else {
            StatementCodegen stmts = new StatementCodegen(codegen,
                firstParamSlot + fn.paramNames.size(), StatementCodegen.ReturnKind.OBJECT);
            stmts.emit(call, fn.bodyBlock);
            call.visitInsn(Opcodes.ACONST_NULL);
            call.visitInsn(Opcodes.ARETURN);
        }
        call.visitMaxs(0, 0);
        call.visitEnd();

        cw.visitEnd();
        classes.put(arrowBinary, cw.toByteArray());
        return arrowInternal;
    }

    private static void pushSmallInt(MethodVisitor mv, int v) {
        if (v >= 0 && v <= 5) mv.visitInsn(Opcodes.ICONST_0 + v);
        else if (v <= Byte.MAX_VALUE) mv.visitIntInsn(Opcodes.BIPUSH, v);
        else mv.visitIntInsn(Opcodes.SIPUSH, v);
    }

    private void emitRootFunctionMethod(ClassWriter cw, String componentInternal,
                                        Class<? extends QObject> rootType,
                                        Ast.FunctionDeclaration fd,
                                        Map<String, Class<? extends QObject>> idTypes,
                                        Map<String, String> rootDeclaredProps,
                                        Map<String, AliasRef> rootAliases,
                                        Map<String, Integer> rootFunctions,
                                        String componentBinaryName,
                                        int[] bindingCounter,
                                        Map<String, byte[]> classes) {
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < fd.paramNames.size(); i++) desc.append("Ljava/lang/Object;");
        desc.append(")Ljava/lang/Object;");

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, fd.name, desc.toString(), null, null);
        mv.visitCode();

        Map<String, Integer> directParams = new LinkedHashMap<>();
        for (int i = 0; i < fd.paramNames.size(); i++) {
            directParams.put(fd.paramNames.get(i), i + 1);
        }
        ExpressionCodegen codegen = ExpressionCodegen.forFunction(componentInternal, rootType, idTypes,
                                                                  directParams, rootDeclaredProps,
                                                                  rootAliases, rootFunctions);
        codegen.setBindingEmitter(childExpr -> emitBindingClass(
            componentInternal, rootType, childExpr, componentBinaryName,
            idTypes, rootDeclaredProps, rootAliases, rootFunctions, bindingCounter, classes));
        codegen.setArrowEmitter(childArrow -> emitArrowClass(
            componentInternal, rootType, childArrow, componentBinaryName,
            idTypes, rootDeclaredProps, rootAliases, rootFunctions, bindingCounter, classes));
        StatementCodegen stmts = new StatementCodegen(codegen, fd.paramNames.size() + 1,
                                                      StatementCodegen.ReturnKind.OBJECT);
        stmts.emit(mv, fd.body);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
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
                                   Map<String, Class<? extends QObject>> out,
                                   boolean insideDelegate) {
        String id = idOf(obj);
        Class<? extends QObject> selfType = registry.resolve(obj.typeName);
        if (id != null && !insideDelegate) {
            if (out.put(id, selfType) != null) {
                throw new IllegalArgumentException("duplicate id: " + id);
            }
        }
        boolean childIsDelegate = DelegateHost.class.isAssignableFrom(selfType);
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.ChildObject) {
                collectIds(((Ast.ChildObject) m).object, registry, out,
                           insideDelegate || childIsDelegate);
            } else if (m instanceof Ast.PropertyBinding) {
                Ast.Value v = ((Ast.PropertyBinding) m).value;
                if (v instanceof Ast.ObjectValue) {
                    collectIds(((Ast.ObjectValue) v).object, registry, out, insideDelegate);
                } else if (v instanceof Ast.ObjectListValue) {
                    for (Ast.ObjectNode n : ((Ast.ObjectListValue) v).objects) {
                        collectIds(n, registry, out, insideDelegate);
                    }
                }
            }
        }
    }

    private static final class DeclaredProp {
        final String name;
        final String typeName;
        final Ast.Value initializer;
        DeclaredProp(String name, String typeName, Ast.Value initializer) {
            this.name = name;
            this.typeName = typeName;
            this.initializer = initializer;
        }
    }

    private static final class AliasDecl {
        final String name;
        final String targetId;
        final String targetProperty;
        AliasDecl(String name, String targetId, String targetProperty) {
            this.name = name;
            this.targetId = targetId;
            this.targetProperty = targetProperty;
        }
    }

    private static AliasDecl parseAlias(DeclaredProp dp) {
        if (dp.initializer == null) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' requires initializer of form id.property");
        }
        if (!(dp.initializer instanceof Ast.ExpressionValue)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' initializer must be expression id.property");
        }
        Ast.Expression e = ((Ast.ExpressionValue) dp.initializer).expr;
        if (!(e instanceof Ast.MemberExpr)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' must reference id.property, got "
                + e.getClass().getSimpleName());
        }
        Ast.MemberExpr m = (Ast.MemberExpr) e;
        if (!(m.target instanceof Ast.IdentifierExpr)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' must reference id.property (target must be id)");
        }
        return new AliasDecl(dp.name, ((Ast.IdentifierExpr) m.target).name, m.property);
    }

    private static void emitAliasLink(MethodVisitor ctor, String componentInternal,
                                      String rootId, Class<? extends QObject> rootType,
                                      Map<String, Class<? extends QObject>> idTypes,
                                      Map<String, String> rootDeclaredProps,
                                      AliasDecl ad) {
        Class<? extends QObject> targetType = idTypes.get(ad.targetId);
        if (targetType == null) {
            throw new IllegalArgumentException(
                "property alias '" + ad.name + "' references unknown id: " + ad.targetId);
        }
        String targetFieldOwner = resolveAliasTargetFieldOwner(
            ad, targetType, rootId, componentInternal, rootDeclaredProps);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        if (targetFieldOwner.equals(componentInternal)) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
        } else {
            String targetInternal = Type.getInternalName(targetType);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, componentInternal, ad.targetId,
                                "L" + targetInternal + ";");
        }
        ctor.visitFieldInsn(Opcodes.GETFIELD, targetFieldOwner, ad.targetProperty, PROPERTY_DESC);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, ad.name, PROPERTY_DESC);
    }

    private static String resolveAliasTargetFieldOwner(AliasDecl ad,
                                                       Class<? extends QObject> targetType,
                                                       String rootId, String componentInternal,
                                                       Map<String, String> rootDeclaredProps) {
        Field f = findPropertyFieldOrNull(targetType, ad.targetProperty);
        if (f != null) {
            return Type.getInternalName(f.getDeclaringClass());
        }
        if (rootId != null && rootId.equals(ad.targetId)
            && rootDeclaredProps.containsKey(ad.targetProperty)) {
            return componentInternal;
        }
        throw new IllegalArgumentException(
            "property alias '" + ad.name + "' target '" + ad.targetId + "." + ad.targetProperty
            + "' resolves to no Property field (v0 allows builtin or root-declared targets only)");
    }

    private static List<DeclaredProp> collectPropertyDecls(Ast.ObjectNode obj, Class<?> ownerType) {
        List<DeclaredProp> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Ast.ObjectMember m : obj.members) {
            if (!(m instanceof Ast.PropertyDeclaration)) continue;
            Ast.PropertyDeclaration pd = (Ast.PropertyDeclaration) m;
            if (pd.isDefault || pd.isRequired || pd.isReadonly) {
                throw new UnsupportedOperationException(
                    "default/required/readonly modifiers not supported: " + pd.name);
            }
            if (!seen.add(pd.name)) {
                throw new IllegalArgumentException("duplicate property declaration: " + pd.name);
            }
            if (findPropertyFieldOrNull(ownerType, pd.name) != null
                || findSignalFieldOrNull(ownerType, pd.name) != null) {
                throw new IllegalArgumentException(
                    "property '" + pd.name + "' shadows existing field on " + ownerType.getName());
            }
            out.add(new DeclaredProp(pd.name, pd.typeName, pd.initializer));
        }
        return out;
    }

    private static void emitInitDeclaredProperty(MethodVisitor ctor, int receiverLocal,
                                                 String ownerInternal, DeclaredProp dp) {
        ctor.visitVarInsn(Opcodes.ALOAD, receiverLocal);
        ctor.visitTypeInsn(Opcodes.NEW, PROPERTY_INTERNAL);
        ctor.visitInsn(Opcodes.DUP);
        emitPropertyDefault(ctor, dp.typeName);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, PROPERTY_INTERNAL,
                             "<init>", "(Ljava/lang/Object;)V", false);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, ownerInternal, dp.name, PROPERTY_DESC);
    }

    private static void emitPropertyDefault(MethodVisitor mv, String typeName) {
        switch (typeName) {
            case "int":
            case "integer":
                mv.visitLdcInsn(0L);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long",
                                   "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case "real":
            case "double":
            case "float":
                mv.visitLdcInsn(0.0d);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double",
                                   "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case "string":
                mv.visitLdcInsn("");
                break;
            case "bool":
            case "boolean":
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean",
                                  "FALSE", "Ljava/lang/Boolean;");
                break;
            default:
                mv.visitInsn(Opcodes.ACONST_NULL);
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
