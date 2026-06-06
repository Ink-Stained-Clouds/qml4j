package io.qml4j.compiler.bytecode;

import io.qml4j.compiler.CompiledUnit;
import io.qml4j.compiler.TypeRegistry;
import io.qml4j.compiler.bytecode.decl.AliasDecl;
import io.qml4j.compiler.bytecode.decl.DeclaredProp;
import io.qml4j.engine.DelegateHost;
import io.qml4j.engine.PropertyChangeSink;
import io.qml4j.engine.SignalRelay;
import io.qml4j.engine.binding.Property;
import io.qml4j.engine.QObject;
import io.qml4j.parser.ast.Ast;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.qml4j.compiler.bytecode.asm.Bytecode.loadLiteral;
import static io.qml4j.compiler.bytecode.asm.Bytecode.pushStringArray;
import static io.qml4j.compiler.bytecode.asm.Descriptors.BINDING_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.DELEGATE_FACTORY_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.DELEGATE_HOST_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.LIST_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.LIST_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.PROPERTY_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.QOBJECT_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.QOBJECT_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_HANDLER_DESC;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SIGNAL_RELAY_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Descriptors.SINK_INTERNAL;
import static io.qml4j.compiler.bytecode.asm.Fields.defaultListFieldFor;
import static io.qml4j.compiler.bytecode.asm.Fields.defaultParentFieldFor;
import static io.qml4j.compiler.bytecode.asm.Fields.findFieldOrNull;
import static io.qml4j.compiler.bytecode.asm.Fields.findListFieldOrNull;
import static io.qml4j.compiler.bytecode.asm.Fields.findPropertyField;
import static io.qml4j.compiler.bytecode.asm.Fields.findPropertyFieldOrNull;
import static io.qml4j.compiler.bytecode.asm.Fields.findSignalFieldOrNull;
import static io.qml4j.compiler.bytecode.asm.Fields.listAcceptsElement;
import static io.qml4j.compiler.bytecode.asm.Fields.propFieldOwnerOrNull;
import static io.qml4j.compiler.bytecode.ast.Ids.collectIds;
import static io.qml4j.compiler.bytecode.ast.Ids.idOf;
import static io.qml4j.compiler.bytecode.decl.PropertyDecls.collectPropertyDecls;
import static io.qml4j.compiler.bytecode.decl.PropertyDecls.emitAliasLink;
import static io.qml4j.compiler.bytecode.decl.PropertyDecls.emitInitDeclaredProperty;
import static io.qml4j.compiler.bytecode.decl.PropertyDecls.parseAlias;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.canHandle;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.collectAliases;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.collectSingletons;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.pushAliases;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.pushSingletons;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.require;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.validateCompiles;
import static io.qml4j.compiler.bytecode.rhino.RhinoScope.validateSource;

public final class QmlCompiler {

    private static final ThreadLocal<int[]> DELEGATE_SCOPE_DEPTH =
        ThreadLocal.withInitial(() -> new int[]{0});

    // Where id'd child objects are stored: the root component (load=ALOAD 0) or,
    // inside a delegate, the delegate-root instance (load=ALOAD delegateLocal).
    private static final class IdSink {
        final String internal;
        final int local;
        IdSink(String internal, int local) { this.internal = internal; this.local = local; }
    }
    private final Deque<IdSink> idSinks = new ArrayDeque<>();

    private final Map<Class<?>, MemberEmitter> memberEmitters = Map.of(
        Ast.PropertyBinding.class, this::emitPropertyBinding,
        Ast.ChildObject.class, this::emitChildObjectMember,
        Ast.BehaviorMember.class, this::emitBehaviorMemberMember,
        Ast.PropertyDeclaration.class, this::emitPropertyDeclarationMember,
        Ast.SignalDeclaration.class, this::rejectSignalDeclaration,
        Ast.FunctionDeclaration.class, this::rejectFunctionDeclaration);

    private static final ThreadLocal<Deque<TypeRegistry>> REGISTRY_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

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
        Label ret = new Label();
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
            AnnotationVisitor av =
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
            // Each root function was registered as a Rhino callable in the ctor; it gets a
            // thin reflective method that forwards to that callable (preserving getMethod
            // identity). The body's eligibility was already validated during ctor emission.
            emitThinRootFunctionMethod(cw, fd);
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
        // A bare call in this object's bindings/handlers resolves to a function declared
        // here (a delegate-local or child-local function) before walking out to the root,
        // so the in-scope function names are this object's locals plus the enclosing ones.
        // Functions are hoisted, so collect them all before emitting any member.
        Map<String, Integer> scopeFunctions = rootFunctions;
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.FunctionDeclaration) {
                Ast.FunctionDeclaration fd = (Ast.FunctionDeclaration) m;
                if (!scopeFunctions.containsKey(fd.name)) {
                    if (scopeFunctions == rootFunctions) scopeFunctions = new LinkedHashMap<>(rootFunctions);
                    scopeFunctions.put(fd.name, fd.paramNames.size());
                }
            }
        }
        EmitContext ctx = new EmitContext(ctor, outerType, outerLocal, registry,
                                          localCounter, bindingCounter, handlerCounter, classes,
                                          componentBinaryName, customSignalOwner, customSignals,
                                          customSignalParams, idTypes, declaredProps, aliases,
                                          scopeFunctions);
        List<Ast.ObjectMember> deferred = new ArrayList<>();
        for (Ast.ObjectMember m : obj.members) {
            if (m instanceof Ast.SignalDeclaration) continue;
            if (m instanceof Ast.FunctionDeclaration) {
                Ast.FunctionDeclaration fd = (Ast.FunctionDeclaration) m;
                if (fd.source == null) {
                    throw new IllegalArgumentException("function '" + fd.name + "' has no captured source");
                }
                require(fd.body, outerType, idTypes, declaredProps, fd.paramNames,
                                      scopeFunctions, customSignals, aliases);
                // Registers the function as a Rhino callable on the object at outerLocal
                // (this for a root function); a root function also gets a thin reflective
                // method emitted in the root-function loop.
                emitRhinoFunction(ctor, outerLocal, fd, idTypes, aliases);
                continue;
            }
            if (isStateAssignment(m)) { deferred.add(m); continue; }
            emitMember(m, ctx);
        }
        for (Ast.ObjectMember m : deferred) {
            ctor.visitMethodInsn(Opcodes.INVOKESTATIC, PROPERTY_INTERNAL,
                                 "drainDeferred", "()V", false);
            emitMember(m, ctx);
        }
    }

    private static boolean isStateAssignment(Ast.ObjectMember m) {
        if (!(m instanceof Ast.PropertyBinding)) return false;
        Ast.PropertyBinding b = (Ast.PropertyBinding) m;
        return b.path.size() == 1 && "state".equals(b.path.get(0));
    }

    private void emitMember(Ast.ObjectMember m, EmitContext ctx) {
        MemberEmitter emitter = memberEmitters.get(m.getClass());
        if (emitter == null) {
            throw new IllegalStateException("unknown member: " + m.getClass());
        }
        emitter.emit(m, ctx);
    }

    private void emitPropertyBinding(Ast.ObjectMember m, EmitContext ctx) {
        MethodVisitor ctor = ctx.ctor;
        Class<? extends QObject> outerType = ctx.outerType;
        int outerLocal = ctx.outerLocal;
        TypeRegistry registry = ctx.registry;
        int[] localCounter = ctx.localCounter;
        int[] bindingCounter = ctx.bindingCounter;
        int[] handlerCounter = ctx.handlerCounter;
        Map<String, byte[]> classes = ctx.classes;
        String componentBinaryName = ctx.componentBinaryName;
        String customSignalOwner = ctx.customSignalOwner;
        Set<String> customSignals = ctx.customSignals;
        Map<String, List<String>> customSignalParams = ctx.customSignalParams;
        Map<String, Class<? extends QObject>> idTypes = ctx.idTypes;
        Map<String, String> declaredProps = ctx.declaredProps;
        Map<String, AliasRef> aliases = ctx.aliases;
        Map<String, Integer> rootFunctions = ctx.rootFunctions;
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
                if (declOwner == null) {
                    throw new IllegalArgumentException(
                        "no property field '" + key + "' on " + outerType.getName());
                }
                if (tryEmitRhinoIifeBinding(ctor, outerType, outerLocal,
                        declOwner, key, sb, idTypes, declaredProps, customSignals, rootFunctions, aliases)) {
                    return;
                }
                // Ineligible only when a free name does not resolve; surface that.
                require(sb.block, outerType, idTypes, declaredProps,
                                      Collections.<String>emptyList(), rootFunctions, customSignals, aliases);
                throw new IllegalArgumentException(
                    "statement-block binding for '" + key + "' could not be compiled");
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
                emitChangeSinkAssignment(ctor, outerType, outerLocal, key, e,
                                         ((Ast.ExpressionValue) b.value).source, idTypes,
                                         declaredProps, aliases, rootFunctions, customSignals);
                return;
            }
            if (e instanceof Ast.LiteralExpr) {
                emitLiteralAssignment(ctor, outerType, outerLocal, key, (Ast.LiteralExpr) e);
            } else {
                emitExpressionBinding(ctor, outerType, outerLocal, componentBinaryName,
                                      bindingCounter, classes, key, e,
                                      ((Ast.ExpressionValue) b.value).source, idTypes,
                                      declaredProps, aliases, rootFunctions, customSignals);
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
            emitGroupedBinding(ctor, outerType, outerLocal, path.get(0), path.get(1),
                               b.value, idTypes, declaredProps, aliases, customSignals, rootFunctions);
            return;
        }
        throw new UnsupportedOperationException("nested grouped property path not supported: " + path);
    }

    private void emitChildObjectMember(Ast.ObjectMember m, EmitContext ctx) {
        emitChildObject(ctx.ctor, ctx.outerType, ctx.outerLocal, ((Ast.ChildObject) m).object, ctx.registry,
                        ctx.localCounter, ctx.bindingCounter, ctx.handlerCounter, ctx.classes, ctx.componentBinaryName,
                        ctx.idTypes, ctx.customSignalParams, ctx.declaredProps, ctx.rootFunctions);
    }

    private void emitBehaviorMemberMember(Ast.ObjectMember m, EmitContext ctx) {
        emitBehaviorMember(ctx.ctor, ctx.outerType, ctx.outerLocal, (Ast.BehaviorMember) m, ctx.registry,
                           ctx.localCounter, ctx.bindingCounter, ctx.handlerCounter, ctx.classes,
                           ctx.componentBinaryName, ctx.idTypes, ctx.customSignalParams, ctx.declaredProps, ctx.rootFunctions);
    }

    private void emitPropertyDeclarationMember(Ast.ObjectMember m, EmitContext ctx) {
        emitPropertyDeclarationInitializer(ctx.ctor, ctx.outerType, ctx.outerLocal, ctx.componentBinaryName,
                                           ctx.bindingCounter, ctx.classes, (Ast.PropertyDeclaration) m,
                                           ctx.idTypes, ctx.declaredProps, ctx.aliases, ctx.rootFunctions,
                                           ctx.registry, ctx.localCounter, ctx.handlerCounter,
                                           ctx.customSignalParams);
    }

    private void rejectSignalDeclaration(Ast.ObjectMember m, EmitContext ctx) {
        throw new IllegalStateException("signal declaration should be handled at object scope");
    }

    private void rejectFunctionDeclaration(Ast.ObjectMember m, EmitContext ctx) {
        throw new IllegalStateException(
            "function declaration should have been handled by emitObjectBody");
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
            if (pd.initializer instanceof Ast.ExpressionValue) {
                Ast.Expression e = ((Ast.ExpressionValue) pd.initializer).expr;
                if (e instanceof Ast.LiteralExpr) {
                    emitLiteralAssignment(ctor, outerType, outerLocal, pd.name, (Ast.LiteralExpr) e);
                } else {
                    emitExpressionBinding(ctor, outerType, outerLocal, componentBinaryName,
                                          bindingCounter, classes, pd.name, e,
                                          ((Ast.ExpressionValue) pd.initializer).source, idTypes,
                                          declaredProps, aliases, rootFunctions, customSignalParams.keySet());
                }
                return;
            }
            if (pd.initializer instanceof Ast.StatementBlockValue) {
                // tryEmitRhinoIifeBinding above returned false: a free name does not resolve.
                require(((Ast.StatementBlockValue) pd.initializer).block, outerType,
                                      idTypes, declaredProps, Collections.<String>emptyList(),
                                      rootFunctions, customSignalParams.keySet(), aliases);
                throw new IllegalArgumentException(
                    "statement-block override for '" + pd.name + "' could not be compiled");
            }
            throw new UnsupportedOperationException(
                "unsupported override initializer for property: " + pd.name);
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
        if (pd.initializer instanceof Ast.ExpressionValue) {
            Ast.Expression e = ((Ast.ExpressionValue) pd.initializer).expr;
            if (e instanceof Ast.LiteralExpr) {
                ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
                ctor.visitFieldInsn(Opcodes.GETFIELD, ownerInternal, pd.name, PROPERTY_DESC);
                loadLiteral(ctor, (Ast.LiteralExpr) e);
                ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                     "set", "(Ljava/lang/Object;)V", false);
                return;
            }
            emitDeclaredPropertyBinding(ctor, outerType, outerLocal, ownerInternal, pd.name, e,
                                        ((Ast.ExpressionValue) pd.initializer).source,
                                        idTypes, declaredProps, aliases, rootFunctions,
                                        customSignalParams.keySet());
            return;
        }
        if (pd.initializer instanceof Ast.StatementBlockValue) {
            // tryEmitRhinoIifeBinding above returned false: a free name does not resolve.
            require(((Ast.StatementBlockValue) pd.initializer).block, outerType,
                                  idTypes, declaredProps, Collections.<String>emptyList(),
                                  rootFunctions, customSignalParams.keySet(), aliases);
            throw new IllegalArgumentException(
                "statement-block default for '" + pd.name + "' could not be compiled");
        }
        throw new UnsupportedOperationException(
            "only expression/object/block initializer supported for property: " + pd.name);
    }

    private void emitDeclaredPropertyBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
                                             int outerLocal,
                                             String ownerInternal, String name, Ast.Expression expr,
                                             String source,
                                             Map<String, Class<? extends QObject>> idTypes,
                                             Map<String, String> declaredProps,
                                             Map<String, AliasRef> aliases,
                                             Map<String, Integer> rootFunctions,
                                             Set<String> customSignals) {
        if (source == null) {
            throw new IllegalArgumentException("binding for '" + name + "' has no captured source");
        }
        require(new Ast.ExprStmt(expr), outerType, idTypes, declaredProps,
                              Collections.<String>emptyList(), rootFunctions, customSignals, aliases);
        emitRhinoBindingBind(ctor, ownerInternal, name, source, outerLocal, idTypes,
                             collectSingletons(expr), collectAliases(expr, aliases));
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
                                          int outerLocal, String name, Ast.Expression expr, String source,
                                          Map<String, Class<? extends QObject>> idTypes,
                                          Map<String, String> declaredProps,
                                          Map<String, AliasRef> aliases,
                                          Map<String, Integer> rootFunctions,
                                          Set<String> customSignals) {
        if (expr instanceof Ast.LiteralExpr) {
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitLdcInsn(name);
            loadLiteral(ctor, (Ast.LiteralExpr) expr);
            ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SINK_INTERNAL,
                                 "addChange", "(Ljava/lang/String;Ljava/lang/Object;)V", true);
            return;
        }
        if (source == null) {
            throw new IllegalArgumentException("change '" + name + "' has no captured source");
        }
        require(new Ast.ExprStmt(expr), outerType, idTypes, declaredProps,
                              Collections.<String>emptyList(), rootFunctions, customSignals, aliases);
        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitLdcInsn(name);
        pushNewRhinoBinding(ctor, source, outerLocal, idTypes,
                            collectSingletons(expr), collectAliases(expr, aliases));
        ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SINK_INTERNAL,
                             "addChangeBinding",
                             "(Ljava/lang/String;L" + BINDING_INTERNAL + ";)V", true);
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
                                       Map<String, Integer> rootFunctions,
                                       Set<String> customSignals) {
        Field f = findPropertyField(outerType, propName);
        String declOwner = Type.getInternalName(f.getDeclaringClass());

        if (source == null) {
            throw new IllegalArgumentException("binding for '" + propName + "' has no captured source");
        }
        require(new Ast.ExprStmt(expr), outerType, idTypes, declaredProps,
                              Collections.<String>emptyList(), rootFunctions, customSignals, aliases);
        emitRhinoBindingBind(ctor, declOwner, propName, source, outerLocal, idTypes,
                             collectSingletons(expr), collectAliases(expr, aliases));
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
        pushNewRhinoBinding(ctor, source, outerLocal, idTypes, singletons, aliases);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                             "bind", "(L" + BINDING_INTERNAL + ";)V", false);
    }

    // Leaves a fresh RhinoBinding over `source` on the operand stack (for Property.bind or
    // a sink's addChangeBinding).
    private void pushNewRhinoBinding(MethodVisitor ctor, String source, int outerLocal,
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
                            idTypes, declaredProps, aliases, rootFunctions, customSignals);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIGNAL_INTERNAL,
                             "connect", "(" + SIGNAL_HANDLER_DESC + ")V", false);
    }

    private static String signalNameFromHandler(String key) {
        if (key.length() < 3 || !key.startsWith("on")) return null;
        char c = key.charAt(2);
        if (!Character.isUpperCase(c)) return null;
        return Character.toLowerCase(c) + key.substring(3);
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
                            rootFunctions, customSignals);
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
                            rootFunctions, customSignals);
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
                            rootFunctions, customSignals);
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
                            rootFunctions, customSignals);
        ctor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SIGNAL_RELAY_INTERNAL,
                             "connectSignal",
                             "(Ljava/lang/String;" + SIGNAL_HANDLER_DESC + ")V", true);
    }

    private static final String RHINO_HANDLER_INTERNAL = "io/qml4j/engine/js/RhinoHandler";
    private static final String RHINO_FUNCTION_INTERNAL = "io/qml4j/engine/js/RhinoFunction";

    // Registers `name` on the QObject at outerLocal as a RhinoFunction callable
    // (__putFunction), reached by both bare and member calls through callQml/callMethod.
    private void emitRhinoFunction(MethodVisitor ctor, int outerLocal, Ast.FunctionDeclaration fd,
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
    // (canHandle), otherwise the ASM-emitted handler class. The signal target
    // is expected to already be on the stack below; callers connect afterwards.
    private void emitHandlerInstance(MethodVisitor ctor, Class<?> outerType, String outerInternal,
                                     String componentInternal, String componentBinaryName, int outerLocal,
                                     Ast.Statement body, String source, List<String> signalParams,
                                     Map<String, Class<? extends QObject>> idTypes,
                                     Map<String, String> declaredProps,
                                     Map<String, AliasRef> aliases,
                                     Map<String, Integer> rootFunctions,
                                     Set<String> customSignals) {
        List<String> params = signalParams != null ? signalParams : Collections.<String>emptyList();
        boolean delegate = inDelegateScope();
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
            "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/String;Z[Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/String;)V", false);
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
        if (!canHandle(blockValue.block, outerType, idTypes, declaredProps,
                              Collections.<String>emptyList(), rootFunctions, customSignals, aliases)) {
            return false;
        }
        String iife = "(function(){" + blockValue.source + "})()";
        validateCompiles(iife);
        emitRhinoBindingBind(ctor, declOwner, propName, iife, outerLocal, idTypes,
                             collectSingletons(blockValue.block), collectAliases(blockValue.block, aliases));
        return true;
    }

    // A grouped binding (`border.color: ...`, `font.pixelSize: ...`): a literal sets the
    // value-type group's Property directly; an expression / function-style body binds a
    // RhinoBinding to it. Throws on a missing group field or property.
    private void emitGroupedBinding(MethodVisitor ctor, Class<? extends QObject> outerType,
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

        // A literal grouped value (`border.width: 2`) is a plain set, not a binding.
        if (value instanceof Ast.ExpressionValue
                && ((Ast.ExpressionValue) value).expr instanceof Ast.LiteralExpr) {
            ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
            ctor.visitFieldInsn(Opcodes.GETFIELD, groupDeclOwner, groupName, "L" + groupTypeInternal + ";");
            ctor.visitFieldInsn(Opcodes.GETFIELD, propDeclOwner, propName, PROPERTY_DESC);
            loadLiteral(ctor, (Ast.LiteralExpr) ((Ast.ExpressionValue) value).expr);
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROPERTY_INTERNAL,
                                 "set", "(Ljava/lang/Object;)V", false);
            return;
        }

        String source;
        Map<String, Class<? extends QObject>> singletons;
        Map<String, AliasRef> usedAliases;
        if (value instanceof Ast.ExpressionValue) {
            Ast.ExpressionValue ev = (Ast.ExpressionValue) value;
            if (ev.source == null) {
                throw new IllegalArgumentException(
                    "grouped binding '" + groupName + "." + propName + "' has no captured source");
            }
            require(new Ast.ExprStmt(ev.expr), outerType, idTypes, declaredProps,
                                  Collections.<String>emptyList(), rootFunctions, customSignals, aliases);
            source = ev.source;
            singletons = collectSingletons(ev.expr);
            usedAliases = collectAliases(ev.expr, aliases);
        } else if (value instanceof Ast.StatementBlockValue) {
            Ast.StatementBlockValue sb = (Ast.StatementBlockValue) value;
            if (sb.source == null) {
                throw new IllegalArgumentException(
                    "grouped binding '" + groupName + "." + propName + "' has no captured source");
            }
            require(sb.block, outerType, idTypes, declaredProps,
                                  Collections.<String>emptyList(), rootFunctions, customSignals, aliases);
            source = "(function(){" + sb.source + "})()";
            singletons = collectSingletons(sb.block);
            usedAliases = collectAliases(sb.block, aliases);
        } else {
            throw new UnsupportedOperationException(
                "only expression/statement grouped bindings supported: " + groupName + "." + propName);
        }

        ctor.visitVarInsn(Opcodes.ALOAD, outerLocal);
        ctor.visitFieldInsn(Opcodes.GETFIELD, groupDeclOwner, groupName, "L" + groupTypeInternal + ";");
        ctor.visitFieldInsn(Opcodes.GETFIELD, propDeclOwner, propName, PROPERTY_DESC);
        emitRhinoBindingFor(ctor, source, outerLocal, idTypes, singletons, usedAliases);
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
}
