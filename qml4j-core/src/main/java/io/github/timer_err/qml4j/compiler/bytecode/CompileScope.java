package io.github.timer_err.qml4j.compiler.bytecode;

import io.github.timer_err.qml4j.compiler.TypeRegistry;
import io.github.timer_err.qml4j.engine.QObject;

import java.util.ArrayDeque;
import java.util.Deque;

// Thread-local compile state shared across the call stack during a single
// QmlCompiler.compile() invocation (and any nested compiles it triggers).
// Exposes the delegate-scope flag and the registry stack as static accessors
// so that RhinoScope, BindingEmitter, and HandlerEmitter need not depend on
// QmlCompiler itself.
public final class CompileScope {

    private CompileScope() {}

    private static final ThreadLocal<int[]> DELEGATE_SCOPE_DEPTH =
        ThreadLocal.withInitial(() -> new int[]{0});

    private static final ThreadLocal<Deque<TypeRegistry>> REGISTRY_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    public static boolean inDelegateScope() {
        return DELEGATE_SCOPE_DEPTH.get()[0] > 0;
    }

    static void enterDelegateScope() {
        DELEGATE_SCOPE_DEPTH.get()[0]++;
    }

    static void exitDelegateScope() {
        DELEGATE_SCOPE_DEPTH.get()[0]--;
    }

    static void pushRegistry(TypeRegistry registry) {
        REGISTRY_STACK.get().push(registry);
    }

    static void popRegistry() {
        REGISTRY_STACK.get().pop();
    }

    public static TypeRegistry currentRegistry() {
        return REGISTRY_STACK.get().peek();
    }

    public static Class<? extends QObject> currentSingletonClass(String name) {
        for (TypeRegistry r : REGISTRY_STACK.get()) {
            Class<? extends QObject> c = r.singletonClass(name);
            if (c != null) return c;
        }
        return null;
    }

    public static void tryResolveType(String name) {
        TypeRegistry r = currentRegistry();
        if (r == null) return;
        if (r.isRegistered(name)) return;
        try { r.resolve(name); }
        catch (IllegalArgumentException ignored) {}
    }
}
