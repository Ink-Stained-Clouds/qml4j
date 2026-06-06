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
import io.qml4j.engine.js.JsRuntime;
import io.qml4j.engine.js.RhinoClosure;
import io.qml4j.parser.ast.Ast;
import org.mozilla.javascript.RhinoException;
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
    private static final String QOBJECT_DESC = "L" + QOBJECT_INTERNAL + ";";
    private static final String DELEGATE_FACTORY_INTERNAL = "io/qml4j/engine/DelegateFactory";
    private static final String DELEGATE_HOST_INTERNAL = "io/qml4j/engine/DelegateHost";
    private static final String SIGNAL_RELAY_INTERNAL = "io/qml4j/engine/SignalRelay";

    private static final ThreadLocal<int[]> DELEGATE_SCOPE_DEPTH =
        ThreadLocal.withInitial(() -> new int[]{0});

    // Where id'd child objects are stored: the root component (load=ALOAD 0) or,
    // inside a delegate, the delegate-root instance (load=ALOAD delegateLocal).
    private static final class IdSink {
        final String internal;
        final int local;
        IdSink(String internal, int local) { this.internal = internal; this.local = local; }
    }
    private final java.util.Deque<IdSink> idSinks = new java.util.ArrayDeque<>();

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
            // Save/restore: a compile can nest (resolving a compound type mid-emit
            // triggers another compile of that type). A bare null-reset here would
            // clobber the enclosing compile's writer/counter.
            ClassWriter prevCw = this.activeComponentCw;
            int prevFactoryCounter = this.factoryCounter;
            this.activeComponentCw = cw;
            this.factoryCounter = 0;
            try {
                if (doc.hasPragma("Singleton")) {
                    emitSingletonAccessor(cw, componentInternal);
                }
                return compileBody(doc, registry, rootType, componentBinaryName,
                                   componentInternal, rootInternal, idTypes, classes, cw);
            } finally {
                this.activeComponentCw = prevCw;
                this.factoryCounter = prevFactoryCounter;
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
        String defaultListAlias = null;
        String defaultListParent = null;
        for (DeclaredProp dp : rootDecls) {
            if ("alias".equals(dp.typeName)) {
                AliasDecl ad = parseAlias(dp);
                rootAliasDecls.add(ad);
                if (ad.isList) {
                    // List alias (id.data/id.children): a List field linked to the
                    // inner container's children. The default one is the component's
                    // default child container.
                    cw.visitField(Opcodes.ACC_PUBLIC, dp.name, LIST_DESC, null, null).visitEnd();
                    if (ad.isDefault) {
                        if (defaultListAlias != null) {
                            throw new IllegalArgumentException("multiple default properties");
                        }
                        defaultListAlias = ad.name;
                        defaultListParent = ad.targetId; // parent default children to it
                    }
                } else if (ad.targetProperty == null) {
                    // Object alias: a field holding the referenced object.
                    cw.visitField(Opcodes.ACC_PUBLIC, dp.name, QOBJECT_DESC, null, null).visitEnd();
                } else {
                    cw.visitField(Opcodes.ACC_PUBLIC, dp.name, PROPERTY_DESC, null, null).visitEnd();
                    rootAliases.put(ad.name, new AliasRef(ad.targetId, ad.targetProperty));
                }
            } else if (dp.isOverride) {
                // Inherited property: no new field; the initializer is applied as
                // a binding to the inherited field (emitPropertyDeclarationInitializer).
                rootRegularDecls.add(dp);
            } else {
                cw.visitField(Opcodes.ACC_PUBLIC, dp.name, PROPERTY_DESC, null, null).visitEnd();
                rootRegularDecls.add(dp);
                rootDeclaredProps.put(dp.name, componentInternal);
            }
        }
        if (defaultListAlias != null) {
            org.objectweb.asm.AnnotationVisitor av =
                cw.visitAnnotation("Lio/qml4j/engine/QmlDefaultList;", true);
            av.visit("value", defaultListAlias);
            if (defaultListParent != null) av.visit("parentField", defaultListParent);
            av.visitEnd();
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
            if (dp.isOverride) continue; // inherited field already exists/initialized
            emitInitDeclaredProperty(ctor, 0, componentInternal, dp);
        }

        String rootId = idOf(doc.root);
        if (rootId != null) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, rootId,
                                "L" + Type.getInternalName(rootType) + ";");
        }

        idSinks.push(new IdSink(componentInternal, 0));
        try {
            emitObjectBody(ctor, rootType, 0, doc.root, registry,
                           localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                           componentInternal, rootSignalNames, customSignalParams, idTypes,
                           rootDeclaredProps, rootAliases, rootFunctions);
        } finally {
            idSinks.pop();
        }

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
            // Eligible functions were registered as Rhino callables in the ctor; they get
            // a thin reflective method that forwards to that callable (preserving getMethod
            // identity). The rest get a full ASM reflective method.
            if (rhinoFunctionEligible(fd, rootType, idTypes, rootDeclaredProps, rootFunctions,
                                      rootSignalNames, rootAliases)) {
                emitThinRootFunctionMethod(cw, fd);
                continue;
            }
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
                Ast.FunctionDeclaration fd = (Ast.FunctionDeclaration) m;
                boolean rhino = rhinoFunctionEligible(fd, outerType, idTypes, declaredProps,
                                                      rootFunctions, customSignals, aliases);
                if (outerLocal == 0) {
                    // Root function: a Rhino callable is registered here; the ASM
                    // reflective method is emitted later only when not eligible.
                    if (rhino) emitRhinoFunction(ctor, 0, fd, idTypes, aliases);
                    continue;
                }
                if (rhino) {
                    emitRhinoFunction(ctor, outerLocal, fd, idTypes, aliases);
                } else {
                    emitChildScopeFunction(ctor, outerType, outerLocal, fd, componentBinaryName,
                                           idTypes, declaredProps, aliases, rootFunctions,
                                           bindingCounter, classes);
                }
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
                // A signal whose name collides with a same-named property (Qt's
                // MouseArea.pressed) is declared as <name>Signal; fall back to it.
                if (signalField == null && signalName != null && !isCustomHandler) {
                    signalField = findSignalFieldOrNull(outerType, signalName + "Signal");
                }
                boolean isRelay = signalName != null && !isCustomHandler && signalField == null
                    && SignalRelay.class.isAssignableFrom(outerType);
                // on<Prop>Changed handler: not a signal, but the named property
                // exists -> connect to its change notification.
                String changeProp = null;
                if (signalName != null && !isCustomHandler && signalField == null && !isRelay
                        && signalName.endsWith("Changed")) {
                    String base = signalName.substring(0, signalName.length() - "Changed".length());
                    if (!base.isEmpty()
                            && (declaredProps.containsKey(base)
                                || findPropertyFieldOrNull(outerType, base) != null)) {
                        changeProp = base;
                    }
                }
                boolean isHandler = isCustomHandler || signalField != null || isRelay || changeProp != null;
                if (isStmtBlock && !isHandler) {
                    Ast.StatementBlockValue sb = (Ast.StatementBlockValue) b.value;
                    String declOwner = propFieldOwnerOrNull(outerType, key, declaredProps);
                    if (declOwner != null && tryEmitRhinoIifeBinding(ctor, outerType, outerLocal,
                            declOwner, key, sb, idTypes, declaredProps, customSignals, rootFunctions, aliases)) {
                        return;
                    }
                    // A function-style binding `prop: { ...; return x }` becomes an
                    // immediately-invoked arrow so the normal ASM binding path applies.
                    Ast.ArrowFunctionExpr fn = new Ast.ArrowFunctionExpr(
                        Collections.<String>emptyList(), null, sb.block);
                    Ast.Expression iife = new Ast.CallExpr(fn, Collections.<Ast.Expression>emptyList());
                    emitExpressionBinding(ctor, outerType, outerLocal, componentBinaryName,
                                          bindingCounter, classes, key, iife, null, idTypes,
                                          declaredProps, aliases, rootFunctions);
                    return;
                }
                if (isHandler) {
                    Ast.ArrowFunctionExpr arrow = arrowHandler(b.value);
                    List<String> handlerParams = arrow != null ? arrow.paramNames
                        : (isCustomHandler ? customSignalParams.get(signalName) : null);
                    Ast.Statement handlerBody = arrow != null ? arrowBody(arrow) : toStatement(b.value);
                    // Arrow-form handlers bind their params as the signal args; the
                    // captured arrow body source runs as `(function(params){ body })`.
                    String handlerSource = arrow != null ? arrow.source : valueSource(b.value);
                    if (isCustomHandler) {
                        emitCustomSignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                                handlerCounter, bindingCounter, classes,
                                                customSignalOwner, signalName, handlerBody, handlerSource, idTypes,
                                                handlerParams, declaredProps, aliases,
                                                rootFunctions, customSignals);
                    } else if (isRelay) {
                        emitRelaySignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                               handlerCounter, bindingCounter, classes, signalName, handlerBody, handlerSource, idTypes,
                                               handlerParams, declaredProps, aliases, rootFunctions, customSignals);
                    } else if (changeProp != null) {
                        emitPropertyChangeHandler(ctor, outerType, outerLocal, componentBinaryName,
                                                  handlerCounter, bindingCounter, classes, changeProp, handlerBody, handlerSource,
                                                  idTypes, declaredProps, aliases, rootFunctions, customSignals);
                    } else {
                        emitSignalHandler(ctor, outerType, outerLocal, componentBinaryName,
                                          handlerCounter, bindingCounter, classes, signalField, handlerBody, handlerSource, idTypes,
                                          handlerParams, declaredProps, aliases, rootFunctions, customSignals);
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
                                          bindingCounter, classes, key, e,
                                          ((Ast.ExpressionValue) b.value).source, idTypes,
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
                                handlerCounter, bindingCounter, classes, signalName, handlerBody, valueSource(b.value),
                                idTypes, declaredProps, aliases, rootFunctions, customSignals);
                return;
            }
            if (path.size() == 2) {
                if (tryEmitRhinoGroupedBinding(ctor, outerType, outerLocal, path.get(0), path.get(1),
                        b.value, idTypes, declaredProps, aliases, customSignals, rootFunctions)) {
                    return;
                }
                Ast.Expression e;
                if (isExprVal) {
                    e = ((Ast.ExpressionValue) b.value).expr;
                } else {
                    // Function-style grouped binding (border.color: { ...; return x }).
                    Ast.Block block = ((Ast.StatementBlockValue) b.value).block;
                    Ast.ArrowFunctionExpr fn = new Ast.ArrowFunctionExpr(
                        Collections.<String>emptyList(), null, block);
                    e = new Ast.CallExpr(fn, Collections.<Ast.Expression>emptyList());
                }
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
                                               idTypes, declaredProps, aliases, rootFunctions,
                                               registry, localCounter, handlerCounter,
                                               customSignalParams);
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
                                                    Map<String, Integer> rootFunctions,
                                                    TypeRegistry registry, int[] localCounter,
                                                    int[] handlerCounter,
                                                    Map<String, List<String>> customSignalParams) {
        if ("alias".equals(pd.typeName)) return;
        if (pd.initializer == null) return;
        // Override of an inherited property (no own field): apply the initializer
        // as a binding/literal to the inherited Property field.
        if (declaredProps.get(pd.name) == null
            && findPropertyFieldOrNull(outerType, pd.name) != null) {
            String overrideOwner = Type.getInternalName(
                findPropertyFieldOrNull(outerType, pd.name).getDeclaringClass());
            if (pd.initializer instanceof Ast.StatementBlockValue
                    && tryEmitRhinoIifeBinding(ctor, outerType, outerLocal, overrideOwner, pd.name,
                        (Ast.StatementBlockValue) pd.initializer, idTypes, declaredProps,
                        customSignalParams.keySet(), rootFunctions, aliases)) {
                return;
            }
            Ast.Expression e;
            if (pd.initializer instanceof Ast.ExpressionValue) {
                e = ((Ast.ExpressionValue) pd.initializer).expr;
            } else if (pd.initializer instanceof Ast.StatementBlockValue) {
                Ast.Block block = ((Ast.StatementBlockValue) pd.initializer).block;
                Ast.ArrowFunctionExpr fn = new Ast.ArrowFunctionExpr(
                    Collections.<String>emptyList(), null, block);
                e = new Ast.CallExpr(fn, Collections.<Ast.Expression>emptyList());
            } else {
                throw new UnsupportedOperationException(
                    "unsupported override initializer for property: " + pd.name);
            }
            if (e instanceof Ast.LiteralExpr) {
                emitLiteralAssignment(ctor, outerType, outerLocal, pd.name, (Ast.LiteralExpr) e);
            } else {
                emitExpressionBinding(ctor, outerType, outerLocal, componentBinaryName,
                                      bindingCounter, classes, pd.name, e, null, idTypes,
                                      declaredProps, aliases, rootFunctions);
            }
            return;
        }
        if (pd.initializer instanceof Ast.ObjectValue) {
            String objOwner = declaredProps.get(pd.name);
            if (objOwner == null) {
                throw new IllegalStateException("declared property not registered: " + pd.name);
            }
            int childLocal = localCounter[0];
            emitChildObjectInto(ctor, outerType, outerLocal,
                                ((Ast.ObjectValue) pd.initializer).object, registry,
                                localCounter, bindingCounter, handlerCounter, classes,
                                componentBinaryName, idTypes, customSignalParams, "",
                                declaredProps, rootFunctions);
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, objOwner, pd.name, PROPERTY_DESC);
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                 "set", "(Ljava/lang/Object;)V", false);
            return;
        }
        String ownerInternal = declaredProps.get(pd.name);
        if (ownerInternal == null) {
            throw new IllegalStateException("declared property not registered: " + pd.name);
        }
        if (pd.initializer instanceof Ast.StatementBlockValue
                && tryEmitRhinoIifeBinding(ctor, outerType, outerLocal, ownerInternal, pd.name,
                    (Ast.StatementBlockValue) pd.initializer, idTypes, declaredProps,
                    customSignalParams.keySet(), rootFunctions, aliases)) {
            return;
        }
        Ast.Expression e;
        if (pd.initializer instanceof Ast.ExpressionValue) {
            e = ((Ast.ExpressionValue) pd.initializer).expr;
        } else if (pd.initializer instanceof Ast.StatementBlockValue) {
            // Function-style property default: { ...; return x } -> IIFE arrow.
            Ast.Block block = ((Ast.StatementBlockValue) pd.initializer).block;
            Ast.ArrowFunctionExpr fn = new Ast.ArrowFunctionExpr(
                Collections.<String>emptyList(), null, block);
            e = new Ast.CallExpr(fn, Collections.<Ast.Expression>emptyList());
        } else {
            throw new UnsupportedOperationException(
                "only expression/object/block initializer supported for property: " + pd.name);
        }
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

    private static String defaultParentFieldFor(Class<?> type) {
        QmlDefaultList ann = type.getAnnotation(QmlDefaultList.class);
        if (ann == null || ann.parentField().isEmpty()) return null;
        return ann.parentField();
    }

    private static Field findFieldOrNull(Class<?> type, String name) {
        try { return type.getField(name); }
        catch (NoSuchFieldException e) { return null; }
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
                Ast.ObjectNode asDelegate = delegateObjectOf(m);
                if (asDelegate != null) {
                    if (delegateNode != null) {
                        throw new IllegalArgumentException(
                            child.typeName + " must declare exactly one delegate child object");
                    }
                    delegateNode = asDelegate;
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
        // Overrides of inherited props create no new field; their initializers are
        // applied as bindings (emitPropertyDeclarationInitializer) via member walk.
        childDecls.removeIf(dp -> dp.isOverride);

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
            IdSink sink = idSinks.isEmpty() ? new IdSink(componentInternal, 0) : idSinks.peek();
            ctor.visitVarInsn(Opcodes.ALOAD, sink.local);
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, sink.internal, childId,
                                "L" + parentInternal + ";");
        }

        Field parentProp = findPropertyFieldOrNull(childType, "parent");
        if (parentProp != null) {
            String declOwner = Type.getInternalName(parentProp.getDeclaringClass());
            ctor.visitVarInsn(Opcodes.ALOAD, childLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, "parent", PROPERTY_DESC);
            // Default-property children render under (and so should be parented to)
            // the inner container the default list aliases, not the component.
            String parentFieldName = defaultParentFieldFor(outerType);
            Field pf = parentFieldName != null && listFieldName.equals(defaultListFieldFor(outerType))
                ? findFieldOrNull(outerType, parentFieldName) : null;
            if (pf != null) {
                ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
                ctor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(pf.getDeclaringClass()),
                                    parentFieldName, Type.getDescriptor(pf.getType()));
            } else {
                ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            }
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
        return emitChildSubclass(subInternal, parentInternal, signalNames, propDecls, null, null);
    }

    private byte[] emitChildSubclass(String subInternal, String parentInternal,
                                     Set<String> signalNames, List<DeclaredProp> propDecls,
                                     String[] extraInterfaces,
                                     Map<String, Class<? extends QObject>> idFields) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                 subInternal, null, parentInternal, extraInterfaces);
        for (String sig : signalNames) {
            cw.visitField(Opcodes.ACC_PUBLIC, sig, SIGNAL_DESC, null, null).visitEnd();
        }
        for (DeclaredProp dp : propDecls) {
            cw.visitField(Opcodes.ACC_PUBLIC, dp.name, PROPERTY_DESC, null, null).visitEnd();
        }
        // Object fields for delegate-local ids (assigned to the delegate root).
        if (idFields != null) {
            for (Map.Entry<String, Class<? extends QObject>> e : idFields.entrySet()) {
                cw.visitField(Opcodes.ACC_PUBLIC, e.getKey(),
                              "L" + Type.getInternalName(e.getValue()) + ";", null, null).visitEnd();
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
        userDecls.removeIf(dp -> dp.isOverride);
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
        // Delegate-local ids: fields on the delegate root, resolved at runtime via
        // delegateContext() walking the parent chain to this DelegateRoot.
        Map<String, Class<? extends QObject>> delIds = new LinkedHashMap<>();
        collectIds(delegateNode, registry, delIds, false);
        for (DeclaredProp dp : fullDecls) delIds.remove(dp.name); // don't shadow index/modelData/user props
        byte[] subBytes = emitChildSubclass(delegateInternal, delBaseInternal, delSignals,
                                            fullDecls, new String[]{"io/qml4j/engine/DelegateRoot"}, delIds);
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
            "(ILjava/lang/Object;Ljava/lang/Object;)L" + QOBJECT_INTERNAL + ";", null, null);
        mv.visitCode();

        int delegateLocal = 4;
        int[] localCounter = {5};

        mv.visitMethodInsn(Opcodes.INVOKESTATIC, PROPERTY_INTERNAL,
                           "pushDeferred", "()V", false);

        mv.visitTypeInsn(Opcodes.NEW, delegateInternal);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, delegateInternal, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, delegateLocal);

        // The delegate root's own id (e.g. `id: wave`) must point at the instance,
        // like the top-level root id; otherwise bindings inside the delegate that
        // reference it (target: wave) resolve to null.
        String selfId = idOf(delegateNode);
        boolean selfIdIsField = selfId != null;
        for (DeclaredProp dp : fullDecls) if (dp.name.equals(selfId)) selfIdIsField = false;
        if (selfIdIsField) {
            mv.visitVarInsn(Opcodes.ALOAD, delegateLocal);
            mv.visitVarInsn(Opcodes.ALOAD, delegateLocal);
            mv.visitFieldInsn(Opcodes.PUTFIELD, delegateInternal, selfId,
                              "L" + Type.getInternalName(delType) + ";");
        }

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
        idSinks.push(new IdSink(delegateInternal, delegateLocal));
        try {
            emitObjectBody(mv, delType, delegateLocal, delegateNode, registry,
                           localCounter, bindingCounter, handlerCounter, classes, componentBinaryName,
                           delegateInternal, delSignals, delSignalParams, idTypes,
                           delDeclaredProps, Collections.<String, AliasRef>emptyMap(), rootFunctions);
        } finally {
            idSinks.pop();
            exitDelegateScope();
        }

        // Set parent before flushing deferred bindings, so a delegate binding's first
        // evaluation can resolve outer-scope names by walking up the parent chain.
        Field parentField = findPropertyField(delType, "parent");
        mv.visitVarInsn(Opcodes.ALOAD, delegateLocal);
        mv.visitFieldInsn(Opcodes.GETFIELD,
                          Type.getInternalName(parentField.getDeclaringClass()),
                          "parent", PROPERTY_DESC);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                           "set", "(Ljava/lang/Object;)V", false);

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
            "(ILjava/lang/Object;Ljava/lang/Object;)L" + QOBJECT_INTERNAL + ";", null, null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 0);
        create.visitFieldInsn(Opcodes.GETFIELD, factoryInternal, "root",
                              "L" + componentInternal + ";");
        create.visitVarInsn(Opcodes.ILOAD, 1);
        create.visitVarInsn(Opcodes.ALOAD, 2);
        create.visitVarInsn(Opcodes.ALOAD, 3);
        create.visitMethodInsn(Opcodes.INVOKEVIRTUAL, componentInternal, "_delegate$" + n,
                               "(ILjava/lang/Object;Ljava/lang/Object;)L" + QOBJECT_INTERNAL + ";", false);
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
        // A single object assigned to a List member (`transitions: Transition{}`) is
        // sugar for a one-element list -- append it, like `states: [State{}]` does.
        if (findListFieldOrNull(outerType, propName) != null) {
            emitChildObjectInto(ctor, outerType, outerLocal, node, registry,
                                localCounter, bindingCounter, handlerCounter, classes,
                                componentBinaryName, idTypes, customSignalParams, propName,
                                declaredProps, rootFunctions);
            return;
        }
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

    // Internal name of the class owning `propName`'s Property field -- a declared
    // property's registered owner, or the declaring class of an inherited field. Null
    // if the property has no field (so the Rhino IIFE path is skipped).
    private static String propFieldOwnerOrNull(Class<?> type, String propName,
                                               Map<String, String> declaredProps) {
        if (declaredProps.containsKey(propName)) return declaredProps.get(propName);
        Field f = findPropertyFieldOrNull(type, propName);
        return f == null ? null : Type.getInternalName(f.getDeclaringClass());
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
                                       String propName, Ast.Expression expr, String source,
                                       Map<String, Class<? extends QObject>> idTypes,
                                       Map<String, String> declaredProps,
                                       Map<String, AliasRef> aliases,
                                       Map<String, Integer> rootFunctions) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());

        // Strangler-fig: route the JS subset Rhino can handle today (pure expression
        // over ids / this-object / inherited properties) onto a RhinoBinding; the ASM
        // backend still handles everything else (calls, Qt, enums, singletons, arrows,
        // delegate scope) until later phases widen rhinoCanHandle.
        if (source != null
                && rhinoCanHandle(expr, outerType, idTypes, declaredProps, aliases)) {
            emitRhinoBindingBind(ctor, declOwner, propName, source, outerLocal, idTypes,
                                 collectSingletons(expr), collectAliases(expr, aliases));
            return;
        }

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

    private static final String RHINO_BINDING_INTERNAL = "io/qml4j/engine/js/RhinoBinding";

    // Binds the property to `new RhinoBinding(source, outer, root, ids)`. The property
    // field is loaded from declOwner; source is the JS the binding evaluates.
    private void emitRhinoBindingBind(MethodVisitor ctor, String declOwner, String propName,
                                      String source, int outerLocal,
                                      Map<String, Class<? extends QObject>> idTypes,
                                      Map<String, Class<? extends QObject>> singletons,
                                      Map<String, AliasRef> aliases) {
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, declOwner, propName, PROPERTY_DESC);
        emitRhinoBindingFor(ctor, source, outerLocal, idTypes, singletons, aliases);
    }

    // With the target Property already on the operand stack, binds it to a fresh
    // RhinoBinding over `source`. Lets callers load any Property (a root field or a
    // grouped border.color/font.pixelSize) and reuse the same binding wiring.
    private void emitRhinoBindingFor(MethodVisitor ctor, String source, int outerLocal,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, Class<? extends QObject>> singletons,
                                     Map<String, AliasRef> aliases) {
        ctor.visitTypeInsn(Opcodes.NEW, RHINO_BINDING_INTERNAL);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitLdcInsn(source);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        pushStringArray(ctor, new ArrayList<>(idTypes.keySet()));
        ctor.visitInsn(inDelegateScope() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        pushSingletons(ctor, singletons);
        pushAliases(ctor, aliases);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, RHINO_BINDING_INTERNAL, "<init>",
                             "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;Z[Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/String;)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "bind", "(L" + BINDING_INTERNAL + ";)V", false);
    }

    // Conservative predicate for the Rhino backend's first phase: a pure expression
    // whose every bare identifier is a scene id, a this-object property, or an
    // inherited property (so QmlScope can resolve it at runtime). Rejects calls,
    // arrows, spreads, templates, array/object literals, aliases, and namespace/
    // singleton members (Qt/Math/Theme/enums) -- those stay on the ASM backend.
    private boolean rhinoCanHandle(Ast.Expression e, Class<? extends QObject> outerType,
                                   Map<String, Class<? extends QObject>> idTypes,
                                   Map<String, String> declaredProps,
                                   Map<String, AliasRef> aliases) {
        if (e instanceof Ast.LiteralExpr) {
            return ((Ast.LiteralExpr) e).kind != Ast.LiteralKind.UNDEFINED;
        }
        if (e instanceof Ast.IdentifierExpr) {
            String n = ((Ast.IdentifierExpr) e).name;
            if (aliases.containsKey(n)) return true;
            if (JS_NAMESPACES.contains(n)) return true;
            if (isSingleton(n)) return true;
            if (inDelegateScope()) {
                // In a delegate, index/modelData/local ids/enclosing-scope names all
                // resolve at runtime through RuntimeHelpers.delegateLookup.
                return true;
            }
            return declaredProps.containsKey(n) || idTypes.containsKey(n)
                || findPropertyFieldOrNull(outerType, n) != null;
        }
        if (e instanceof Ast.MemberExpr) {
            return rhinoCanHandle(((Ast.MemberExpr) e).target, outerType, idTypes, declaredProps, aliases);
        }
        if (e instanceof Ast.IndexExpr) {
            Ast.IndexExpr ix = (Ast.IndexExpr) e;
            return rhinoCanHandle(ix.target, outerType, idTypes, declaredProps, aliases)
                && rhinoCanHandle(ix.index, outerType, idTypes, declaredProps, aliases);
        }
        if (e instanceof Ast.BinaryExpr) {
            Ast.BinaryExpr b = (Ast.BinaryExpr) e;
            return rhinoCanHandle(b.left, outerType, idTypes, declaredProps, aliases)
                && rhinoCanHandle(b.right, outerType, idTypes, declaredProps, aliases);
        }
        if (e instanceof Ast.UnaryExpr) {
            return rhinoCanHandle(((Ast.UnaryExpr) e).operand, outerType, idTypes, declaredProps, aliases);
        }
        if (e instanceof Ast.CondExpr) {
            Ast.CondExpr c = (Ast.CondExpr) e;
            return rhinoCanHandle(c.cond, outerType, idTypes, declaredProps, aliases)
                && rhinoCanHandle(c.thenBranch, outerType, idTypes, declaredProps, aliases)
                && rhinoCanHandle(c.elseBranch, outerType, idTypes, declaredProps, aliases);
        }
        if (e instanceof Ast.CallExpr) {
            // A call with a receiver (namespace fn like Math.max / Qt.rgba, or a method
            // on an id/property object) whose receiver and args all resolve. Bare calls
            // (no receiver) and Qt.binding/callLater (need scope capture) stay on ASM.
            Ast.CallExpr call = (Ast.CallExpr) e;
            if (!(call.callee instanceof Ast.MemberExpr)) return false;
            Ast.MemberExpr m = (Ast.MemberExpr) call.callee;
            if (m.target instanceof Ast.IdentifierExpr) {
                String ns = ((Ast.IdentifierExpr) m.target).name;
                if ("Qt".equals(ns) && ("binding".equals(m.property) || "callLater".equals(m.property))) {
                    return false;
                }
            }
            if (!rhinoCanHandle(m.target, outerType, idTypes, declaredProps, aliases)) return false;
            for (Ast.Expression arg : call.args) {
                if (!rhinoCanHandle(arg, outerType, idTypes, declaredProps, aliases)) return false;
            }
            return true;
        }
        return false;
    }

    private static final java.util.Set<String> JS_NAMESPACES = new java.util.HashSet<>(java.util.Arrays.asList(
        "Qt", "Math", "Text", "Font", "Easing", "console", "JSON",
        "Number", "parseInt", "parseFloat", "isNaN"));

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
                                 String signalName, Ast.Statement body, String source,
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
                            idTypes, declaredProps, aliases, rootFunctions, customSignals,
                            handlerCounter, bindingCounter, classes);
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

    // A delegate declared either as the host's single child object (`Repeater { Foo {} }`)
    // or via the `delegate:` property (`Repeater { delegate: Foo {} }`). Both forms map
    // to the same delegate node; returns null for any other member.
    private static Ast.ObjectNode delegateObjectOf(Ast.ObjectMember m) {
        if (m instanceof Ast.ChildObject) {
            return ((Ast.ChildObject) m).object;
        }
        if (m instanceof Ast.PropertyBinding) {
            Ast.PropertyBinding pb = (Ast.PropertyBinding) m;
            if (pb.path.size() == 1 && "delegate".equals(pb.path.get(0))
                    && pb.value instanceof Ast.ObjectValue) {
                return ((Ast.ObjectValue) pb.value).object;
            }
        }
        return null;
    }

    private static Ast.Statement toStatement(Ast.Value v) {
        if (v instanceof Ast.StatementBlockValue) {
            return ((Ast.StatementBlockValue) v).block;
        }
        Ast.Expression expr = ((Ast.ExpressionValue) v).expr;
        return new Ast.Block(Collections.<Ast.Statement>singletonList(new Ast.ExprStmt(expr)));
    }

    // The raw JS source captured for a handler body, whether a statement block
    // (`{ ... }`) or a single expression (`onClicked: foo()`). Null when the parser
    // could not capture it, which routes the handler to the ASM backend.
    private static String valueSource(Ast.Value v) {
        if (v instanceof Ast.StatementBlockValue) return ((Ast.StatementBlockValue) v).source;
        if (v instanceof Ast.ExpressionValue) return ((Ast.ExpressionValue) v).source;
        return null;
    }

    // `onPressed: (mouse) => {...}` — Qt's typed handler form. The arrow's params
    // bind to the signal args; its body becomes the handler body.
    private static Ast.ArrowFunctionExpr arrowHandler(Ast.Value v) {
        if (!(v instanceof Ast.ExpressionValue)) return null;
        Ast.Expression e = ((Ast.ExpressionValue) v).expr;
        return e instanceof Ast.ArrowFunctionExpr ? (Ast.ArrowFunctionExpr) e : null;
    }

    private static Ast.Statement arrowBody(Ast.ArrowFunctionExpr arrow) {
        if (arrow.bodyBlock != null) return arrow.bodyBlock;
        return new Ast.Block(Collections.<Ast.Statement>singletonList(new Ast.ExprStmt(arrow.bodyExpr)));
    }

    private void emitCustomSignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                         int outerLocal, String componentBinaryName,
                                         int[] handlerCounter, int[] bindingCounter, Map<String, byte[]> classes,
                                         String signalOwnerInternal, String signalName,
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
        ctor.visitFieldInsn(Opcodes.GETFIELD, signalOwnerInternal, signalName, SIGNAL_DESC);
        emitHandlerInstance(ctor, outerType, outerInternal, componentInternal, componentBinaryName,
                            outerLocal, body, source, signalParams, idTypes, declaredProps, aliases,
                            rootFunctions, customSignals, handlerCounter, bindingCounter, classes);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    private void emitSignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                   int outerLocal, String componentBinaryName,
                                   int[] handlerCounter, int[] bindingCounter, Map<String, byte[]> classes,
                                   Field signalField, Ast.Statement body, String source,
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
                            rootFunctions, customSignals, handlerCounter, bindingCounter, classes);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    // on<Prop>Changed: run the handler whenever the property's value changes.
    private void emitPropertyChangeHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
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
                            rootFunctions, customSignals, handlerCounter, bindingCounter, classes);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "addChangeHandler", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    private void emitRelaySignalHandler(MethodVisitor ctor, Class<? extends QObject> outerType,
                                        int outerLocal, String componentBinaryName,
                                        int[] handlerCounter, int[] bindingCounter, Map<String, byte[]> classes,
                                        String signalName, Ast.Statement body, String source,
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
                            rootFunctions, customSignals, handlerCounter, bindingCounter, classes);
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

    private static final String RHINO_HANDLER_INTERNAL = "io/qml4j/engine/js/RhinoHandler";
    private static final String RHINO_FUNCTION_INTERNAL = "io/qml4j/engine/js/RhinoFunction";

    // A QML function runs on Rhino when its source is captured, it is not in a delegate
    // scope, and every free name resolves at runtime (no alias / deferred Qt helper). A
    // root function additionally keeps a thin reflective method that forwards to its
    // RhinoFunction (emitThinRootFunctionMethod), so callers invoking it via getMethod
    // still find it.
    private boolean rhinoFunctionEligible(Ast.FunctionDeclaration fd, Class<?> contextType,
                                          Map<String, Class<? extends QObject>> idTypes,
                                          Map<String, String> declaredProps,
                                          Map<String, Integer> rootFunctions,
                                          Set<String> customSignals,
                                          Map<String, AliasRef> aliases) {
        return fd.source != null && !inDelegateScope()
            && handlerCanHandle(fd.body, contextType, idTypes, declaredProps, fd.paramNames,
                                rootFunctions, customSignals, aliases);
    }

    // Registers `name` on the QObject at outerLocal as a RhinoFunction callable
    // (__putFunction), reached by both bare and member calls through callQml/callMethod.
    private void emitRhinoFunction(MethodVisitor ctor, int outerLocal, Ast.FunctionDeclaration fd,
                                   Map<String, Class<? extends QObject>> idTypes,
                                   Map<String, AliasRef> aliases) {
        validateRhinoSource(fd.source, fd.paramNames);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(fd.name);
        ctor.visitTypeInsn(Opcodes.NEW, RHINO_FUNCTION_INTERNAL);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitLdcInsn(fd.source);
        pushStringArray(ctor, fd.paramNames);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        pushStringArray(ctor, new ArrayList<>(idTypes.keySet()));
        ctor.visitInsn(inDelegateScope() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        pushSingletons(ctor, collectSingletons(fd.body));
        pushAliases(ctor, collectAliases(fd.body, aliases));
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, RHINO_FUNCTION_INTERNAL, "<init>",
            "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;Z[Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/String;)V", false);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, QOBJECT_INTERNAL, "__putFunction",
                             "(Ljava/lang/String;Lio/qml4j/engine/Callable;)V", false);
    }

    // Pushes a SignalHandler instance onto the stack: a RhinoHandler when the body's
    // raw JS source is available and every bare name it touches resolves at runtime
    // (handlerCanHandle), otherwise the ASM-emitted handler class. The signal target
    // is expected to already be on the stack below; callers connect afterwards.
    private void emitHandlerInstance(MethodVisitor ctor, Class<?> outerType, String outerInternal,
                                     String componentInternal, String componentBinaryName, int outerLocal,
                                     Ast.Statement body, String source, List<String> signalParams,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, String> declaredProps,
                                     Map<String, AliasRef> aliases,
                                     Map<String, Integer> rootFunctions,
                                     Set<String> customSignals,
                                     int[] handlerCounter, int[] bindingCounter,
                                     Map<String, byte[]> classes) {
        List<String> params = signalParams != null ? signalParams : Collections.<String>emptyList();
        boolean delegate = inDelegateScope();
        // In a delegate, a bare-identifier call resolves through the parent chain, which
        // the Rhino delegate scope doesn't walk yet -- keep those handlers on ASM.
        boolean delegateOk = !delegate || !AstScan.hasBareCall(body);
        if (source != null && delegateOk
                && handlerCanHandle(body, outerType, idTypes, declaredProps, params, rootFunctions,
                                    customSignals, aliases)) {
            validateRhinoSource(source, params);
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
                "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;Z[Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/String;)V", false);
            return;
        }
        int n = handlerCounter[0]++;
        String handlerBinaryName = componentBinaryName + "$Handler$" + n;
        String handlerInternal = handlerBinaryName.replace('.', '/');
        byte[] handlerBytes = emitHandlerClass(handlerInternal, outerInternal, outerType, body,
                                               componentInternal, componentBinaryName, idTypes,
                                               params, declaredProps, aliases, rootFunctions,
                                               bindingCounter, classes);
        classes.put(handlerBinaryName, handlerBytes);
        ctor.visitTypeInsn(Opcodes.NEW, handlerInternal);
        ctor.visitInsn(Opcodes.DUP);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, handlerInternal, "<init>",
                             "(L" + outerInternal + ";L" + componentInternal + ";)V", false);
    }

    // Compile the wrapped JS eagerly so a syntax error (a stray break, a malformed
    // expression) surfaces at QML-compile time -- parity with the ASM backend's
    // early diagnostics -- and warms the shared Script cache the runtime reuses. The
    // wrapper must match RhinoClosure exactly, or a body with a top-level return
    // would falsely fail validation.
    private static void validateRhinoSource(String source, List<String> params) {
        validateRhinoCompiles(RhinoClosure.wrap(source, params));
    }

    private static void validateRhinoCompiles(String js) {
        try {
            JsRuntime.compile(js);
        } catch (RhinoException e) {
            throw new IllegalArgumentException("invalid JS: " + e.getMessage(), e);
        }
    }

    // A function-style property binding (`prop: { ...; return x }`) runs on Rhino as an
    // immediately-invoked function, when the body is eligible (every free name resolves,
    // no Qt.binding, not in a delegate scope). Returns false to leave it on the ASM IIFE
    // path. declOwner is the internal name of the class owning the property field.
    private boolean tryEmitRhinoIifeBinding(MethodVisitor ctor, Class<?> outerType, int outerLocal,
                                            String declOwner, String propName,
                                            Ast.StatementBlockValue blockValue,
                                            Map<String, Class<? extends QObject>> idTypes,
                                            Map<String, String> declaredProps,
                                            Set<String> customSignals,
                                            Map<String, Integer> rootFunctions,
                                            Map<String, AliasRef> aliases) {
        if (blockValue.source == null) return false;
        if (!handlerCanHandle(blockValue.block, outerType, idTypes, declaredProps,
                              Collections.<String>emptyList(), rootFunctions, customSignals, aliases)) {
            return false;
        }
        String iife = "(function(){" + blockValue.source + "})()";
        validateRhinoCompiles(iife);
        emitRhinoBindingBind(ctor, declOwner, propName, iife, outerLocal, idTypes,
                             collectSingletons(blockValue.block), collectAliases(blockValue.block, aliases));
        return true;
    }

    // A grouped binding (`border.color: ...`, `font.pixelSize: ...`) on Rhino: binds a
    // RhinoBinding to the value-type group's Property. Handles both the simple
    // expression form (rhinoCanHandle) and the function-style IIFE form
    // (handlerCanHandle). Returns false to leave it on the ASM grouped path.
    private boolean tryEmitRhinoGroupedBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
                                               int outerLocal, String groupName, String propName,
                                               Ast.Value value,
                                               Map<String, Class<? extends QObject>> idTypes,
                                               Map<String, String> declaredProps,
                                               Map<String, AliasRef> aliases,
                                               Set<String> customSignals,
                                               Map<String, Integer> rootFunctions) {
        Field groupField;
        try {
            groupField = outerType.getField(groupName);
        } catch (NoSuchFieldException e) {
            return false;
        }
        if (Property.class.isAssignableFrom(groupField.getType())) return false;
        Class<?> groupType = groupField.getType();
        Field propField = findPropertyFieldOrNull(groupType, propName);
        if (propField == null) return false;

        String source;
        Map<String, Class<? extends QObject>> singletons;
        Map<String, AliasRef> usedAliases;
        if (value instanceof Ast.ExpressionValue) {
            Ast.ExpressionValue ev = (Ast.ExpressionValue) value;
            if (ev.source == null || ev.expr instanceof Ast.LiteralExpr
                    || !rhinoCanHandle(ev.expr, outerType, idTypes, declaredProps, aliases)) {
                return false;
            }
            source = ev.source;
            singletons = collectSingletons(ev.expr);
            usedAliases = collectAliases(ev.expr, aliases);
        } else if (value instanceof Ast.StatementBlockValue) {
            Ast.StatementBlockValue sb = (Ast.StatementBlockValue) value;
            if (sb.source == null
                    || !handlerCanHandle(sb.block, outerType, idTypes, declaredProps,
                                         Collections.<String>emptyList(), rootFunctions, customSignals, aliases)) {
                return false;
            }
            source = "(function(){" + sb.source + "})()";
            singletons = collectSingletons(sb.block);
            usedAliases = collectAliases(sb.block, aliases);
        } else {
            return false;
        }
        validateRhinoCompiles(source);

        String groupDeclOwner = Type.getInternalName(groupField.getDeclaringClass());
        String propDeclOwner = Type.getInternalName(propField.getDeclaringClass());
        String groupTypeInternal = Type.getInternalName(groupType);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, groupDeclOwner, groupName, "L" + groupTypeInternal + ";");
        ctor.visitFieldInsn(Opcodes.GETFIELD, propDeclOwner, propName, PROPERTY_DESC);
        emitRhinoBindingFor(ctor, source, outerLocal, idTypes, singletons, usedAliases);
        return true;
    }

    private static void pushStringArray(MethodVisitor mv, List<String> items) {
        mv.visitLdcInsn(items.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int i = 0; i < items.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(i);
            mv.visitLdcInsn(items.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    // Conservative predicate for the Rhino handler/function backend: every free name
    // the body reads or writes must be one the runtime QmlScope can resolve -- a
    // scene id, a declared/inherited property field, a root function, a Java method,
    // or a shared global. Aliases, singletons and value-type group fields fall into
    // none of these and so keep the body on the ASM backend (no silent no-op).
    private boolean handlerCanHandle(Ast.Statement body, Class<?> outerType,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, String> declaredProps,
                                     List<String> signalParams,
                                     Map<String, Integer> rootFunctions,
                                     Set<String> customSignals,
                                     Map<String, AliasRef> aliases) {
        // Qt.binding / Qt.callLater are not bridged into the Rhino globals yet.
        if (AstScan.usesDeferredQtHelper(body)) return false;
        Set<String> bound = new java.util.HashSet<>(signalParams);
        for (String name : FreeIdentifiers.collect(body, bound)) {
            if (!resolvableByRhino(name, outerType, idTypes, declaredProps, rootFunctions,
                                   customSignals, aliases)) {
                return false;
            }
        }
        return true;
    }

    private boolean resolvableByRhino(String name, Class<?> outerType,
                                      Map<String, Class<? extends QObject>> idTypes,
                                      Map<String, String> declaredProps,
                                      Map<String, Integer> rootFunctions,
                                      Set<String> customSignals,
                                      Map<String, AliasRef> aliases) {
        if (aliases.containsKey(name)) return true;
        if (isSingleton(name)) return true;
        if (inDelegateScope()) {
            // In a delegate, index/modelData/local-id/enclosing-scope names resolve at
            // runtime via RuntimeHelpers.delegateLookup (matching rhinoCanHandle's
            // delegate branch).
            return true;
        }
        if (idTypes.containsKey(name) || declaredProps.containsKey(name)
                || rootFunctions.containsKey(name) || customSignals.contains(name)
                || JS_GLOBALS.contains(name)) {
            return true;
        }
        try {
            outerType.getField(name);
            return true;
        } catch (NoSuchFieldException ignore) {
        }
        for (Method m : outerType.getMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }

    // Whether `name` resolves to a QML singleton (Theme, ...). Gated on an upper-case
    // initial (singletons/types are capitalized) so plain property/id identifiers don't
    // trigger a type-resolution attempt.
    private boolean isSingleton(String name) {
        if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) return false;
        tryResolveType(name);
        return currentSingletonClass(name) != null;
    }

    // The singletons a body/expression references, name -> the specific generated class,
    // collected from its free identifiers so each binding carries exactly the classes it
    // uses (no global name lookup).
    private Map<String, Class<? extends QObject>> collectSingletons(Ast.Statement body) {
        Map<String, Class<? extends QObject>> m = new LinkedHashMap<>();
        for (String n : FreeIdentifiers.collect(body, java.util.Collections.<String>emptySet())) {
            if (isSingleton(n)) m.put(n, currentSingletonClass(n));
        }
        return m;
    }

    private Map<String, Class<? extends QObject>> collectSingletons(Ast.Expression e) {
        return collectSingletons(new Ast.ExprStmt(e));
    }

    // The aliases a body/expression references, name -> AliasRef, so each binding carries
    // exactly the aliases it uses (resolved per-binding in QmlScope, never a global lookup).
    private Map<String, AliasRef> collectAliases(Ast.Statement body, Map<String, AliasRef> aliases) {
        if (aliases.isEmpty()) return java.util.Collections.emptyMap();
        Map<String, AliasRef> m = new LinkedHashMap<>();
        for (String n : FreeIdentifiers.collect(body, java.util.Collections.<String>emptySet())) {
            AliasRef a = aliases.get(n);
            if (a != null) m.put(n, a);
        }
        return m;
    }

    private Map<String, AliasRef> collectAliases(Ast.Expression e, Map<String, AliasRef> aliases) {
        return collectAliases(new Ast.ExprStmt(e), aliases);
    }

    // Pushes the alias specs (String[] of "name\0targetId\0targetProperty") for a binding,
    // so QmlScope can expand each alias to its target id's property at runtime. The NUL
    // separator can't appear in identifiers, so QmlScope.aliasMap splits on it cleanly.
    private void pushAliases(MethodVisitor mv, Map<String, AliasRef> aliases) {
        List<String> specs = new ArrayList<>();
        for (Map.Entry<String, AliasRef> e : aliases.entrySet()) {
            specs.add(e.getKey() + "\u0000" + e.getValue().targetId + "\u0000" + e.getValue().targetProperty);
        }
        pushStringArray(mv, specs);
    }

    // Pushes the singleton names (String[]) and their classes (Class[]) for a binding,
    // so the runtime QmlScope can resolve them per-binding without a global registry.
    private void pushSingletons(MethodVisitor mv, Map<String, Class<? extends QObject>> singletons) {
        List<String> names = new ArrayList<>(singletons.keySet());
        pushStringArray(mv, names);
        mv.visitLdcInsn(names.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        for (int i = 0; i < names.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(i);
            mv.visitLdcInsn(Type.getObjectType(Type.getInternalName(singletons.get(names.get(i)))));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private static final Set<String> JS_GLOBALS = new java.util.HashSet<>(java.util.Arrays.asList(
        "Qt", "Math", "JSON", "console", "Easing", "Text", "Font",
        "Object", "Array", "String", "Number", "Boolean", "Date", "RegExp",
        "parseInt", "parseFloat", "isNaN", "isFinite", "NaN", "Infinity", "undefined"));

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

    // A reflective method `name(Object...)` that forwards to the function's RhinoFunction
    // (registered via __putFunction in the ctor). Keeps the Java-method identity that
    // callers using getClass().getMethod(name) rely on, while the body runs on Rhino.
    private void emitThinRootFunctionMethod(ClassWriter cw, Ast.FunctionDeclaration fd) {
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
        final boolean isDefault;
        final boolean isOverride;  // redeclares an inherited Property -> init it, no new field
        DeclaredProp(String name, String typeName, Ast.Value initializer) {
            this(name, typeName, initializer, false, false);
        }
        DeclaredProp(String name, String typeName, Ast.Value initializer, boolean isDefault) {
            this(name, typeName, initializer, isDefault, false);
        }
        DeclaredProp(String name, String typeName, Ast.Value initializer, boolean isDefault, boolean isOverride) {
            this.name = name;
            this.typeName = typeName;
            this.initializer = initializer;
            this.isDefault = isDefault;
            this.isOverride = isOverride;
        }
    }

    private static final class AliasDecl {
        final String name;
        final String targetId;
        final String targetProperty;
        final boolean isList;     // alias to id.data / id.children (a List)
        final boolean isDefault;  // default property alias -> the default child container
        AliasDecl(String name, String targetId, String targetProperty) {
            this(name, targetId, targetProperty, false, false);
        }
        AliasDecl(String name, String targetId, String targetProperty, boolean isList, boolean isDefault) {
            this.name = name;
            this.targetId = targetId;
            this.targetProperty = targetProperty;
            this.isList = isList;
            this.isDefault = isDefault;
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
        if (e instanceof Ast.IdentifierExpr) {
            // Object alias: `property alias foo: someId` exposes the object itself.
            return new AliasDecl(dp.name, ((Ast.IdentifierExpr) e).name, null, false, dp.isDefault);
        }
        if (!(e instanceof Ast.MemberExpr)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' must reference id or id.property, got "
                + e.getClass().getSimpleName());
        }
        Ast.MemberExpr m = (Ast.MemberExpr) e;
        if (!(m.target instanceof Ast.IdentifierExpr)) {
            throw new IllegalArgumentException(
                "property alias '" + dp.name + "' must reference id.property (target must be id)");
        }
        boolean isList = "data".equals(m.property) || "children".equals(m.property);
        return new AliasDecl(dp.name, ((Ast.IdentifierExpr) m.target).name, m.property, isList, dp.isDefault);
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
        if (ad.isList) {
            // this.NAME = this.targetId.children  (share the inner container's list)
            Field childrenField;
            try {
                childrenField = targetType.getField("children");
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(
                    "list alias '" + ad.name + "' target '" + ad.targetId + "' has no children list");
            }
            String childrenOwner = Type.getInternalName(childrenField.getDeclaringClass());
            String targetInternal = Type.getInternalName(targetType);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, componentInternal, ad.targetId,
                                "L" + targetInternal + ";");
            ctor.visitFieldInsn(Opcodes.GETFIELD, childrenOwner, "children", LIST_DESC);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, ad.name, LIST_DESC);
            return;
        }
        if (ad.targetProperty == null) {
            // Object alias: this.NAME = this.targetId
            String targetInternal = Type.getInternalName(targetType);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETFIELD, componentInternal, ad.targetId,
                                "L" + targetInternal + ";");
            ctor.visitFieldInsn(Opcodes.PUTFIELD, componentInternal, ad.name, QOBJECT_DESC);
            return;
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
            // readonly/required are accepted as plain properties (v0 doesn't
            // enforce immutability or instantiation-time requirement). `default`
            // is only supported on a list alias (default property alias x: id.data).
            if (pd.isDefault && !"alias".equals(pd.typeName)) {
                throw new UnsupportedOperationException(
                    "default property modifier supported only on a list alias: " + pd.name);
            }
            if (!seen.add(pd.name)) {
                throw new IllegalArgumentException("duplicate property declaration: " + pd.name);
            }
            if (findSignalFieldOrNull(ownerType, pd.name) != null) {
                throw new IllegalArgumentException(
                    "property '" + pd.name + "' shadows existing signal on " + ownerType.getName());
            }
            // Redeclaring an inherited Property (Qt allows `property bool enabled`
            // on a type that already has enabled) -> treat as an override: no new
            // field, the initializer just sets the inherited one.
            boolean isOverride = !"alias".equals(pd.typeName)
                && findPropertyFieldOrNull(ownerType, pd.name) != null;
            out.add(new DeclaredProp(pd.name, pd.typeName, pd.initializer, pd.isDefault, isOverride));
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
