package io.github.timer_err.qml4j.compiler.bytecode.member;

import io.github.timer_err.qml4j.compiler.TypeRegistry;
import io.github.timer_err.qml4j.compiler.bytecode.AliasRef;
import io.github.timer_err.qml4j.engine.QObject;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Carries the shared emit state for one object body: the constructor visitor
// being written into, the object's compile-time type and constructor local, the
// type registry, the running counters (local slots, binding/handler class names),
// the output class map and component binary name, the custom-signal metadata, and
// the in-scope id types / declared properties / aliases / hoisted functions used
// to resolve names. Replaces the parameter sprawl threaded through member emit.
public final class EmitContext {

    public final MethodVisitor ctor;
    public final Class<? extends QObject> outerType;
    public final int outerLocal;
    public final TypeRegistry registry;
    public final int[] localCounter;
    public final int[] bindingCounter;
    public final int[] handlerCounter;
    public final Map<String, byte[]> classes;
    public final String componentBinaryName;
    public final String customSignalOwner;
    public final Set<String> customSignals;
    public final Map<String, List<String>> customSignalParams;
    public final Map<String, Class<? extends QObject>> idTypes;
    public final Map<String, String> declaredProps;
    public final Map<String, AliasRef> aliases;
    public final Map<String, Integer> rootFunctions;

    public EmitContext(MethodVisitor ctor, Class<? extends QObject> outerType, int outerLocal,
                       TypeRegistry registry, int[] localCounter, int[] bindingCounter,
                       int[] handlerCounter, Map<String, byte[]> classes,
                       String componentBinaryName, String customSignalOwner,
                       Set<String> customSignals, Map<String, List<String>> customSignalParams,
                       Map<String, Class<? extends QObject>> idTypes,
                       Map<String, String> declaredProps,
                       Map<String, AliasRef> aliases, Map<String, Integer> rootFunctions) {
        this.ctor = ctor;
        this.outerType = outerType;
        this.outerLocal = outerLocal;
        this.registry = registry;
        this.localCounter = localCounter;
        this.bindingCounter = bindingCounter;
        this.handlerCounter = handlerCounter;
        this.classes = classes;
        this.componentBinaryName = componentBinaryName;
        this.customSignalOwner = customSignalOwner;
        this.customSignals = customSignals;
        this.customSignalParams = customSignalParams;
        this.idTypes = idTypes;
        this.declaredProps = declaredProps;
        this.aliases = aliases;
        this.rootFunctions = rootFunctions;
    }
}
