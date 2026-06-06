package io.qml4j.compiler.bytecode;

import io.qml4j.compiler.TypeRegistry;
import io.qml4j.engine.QObject;
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
final class EmitContext {

    final MethodVisitor ctor;
    final Class<? extends QObject> outerType;
    final int outerLocal;
    final TypeRegistry registry;
    final int[] localCounter;
    final int[] bindingCounter;
    final int[] handlerCounter;
    final Map<String, byte[]> classes;
    final String componentBinaryName;
    final String customSignalOwner;
    final Set<String> customSignals;
    final Map<String, List<String>> customSignalParams;
    final Map<String, Class<? extends QObject>> idTypes;
    final Map<String, String> declaredProps;
    final Map<String, AliasRef> aliases;
    final Map<String, Integer> rootFunctions;

    EmitContext(MethodVisitor ctor, Class<? extends QObject> outerType, int outerLocal,
                TypeRegistry registry, int[] localCounter, int[] bindingCounter, int[] handlerCounter,
                Map<String, byte[]> classes, String componentBinaryName, String customSignalOwner,
                Set<String> customSignals, Map<String, List<String>> customSignalParams,
                Map<String, Class<? extends QObject>> idTypes, Map<String, String> declaredProps,
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
