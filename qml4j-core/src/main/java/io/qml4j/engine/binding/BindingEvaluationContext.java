package io.qml4j.engine.binding;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

public final class BindingEvaluationContext {

    private static final ThreadLocal<Deque<Set<Property<?>>>> STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    private BindingEvaluationContext() {}

    public static void push(Set<Property<?>> reads) {
        STACK.get().push(reads);
    }

    public static void pop() {
        STACK.get().pop();
    }

    public static void recordRead(Property<?> p) {
        Deque<Set<Property<?>>> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.peek().add(p);
        }
    }
}
