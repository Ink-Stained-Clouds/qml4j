package io.qml4j.engine.binding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class Property<T> {

    public interface WriteInterceptor<T> {
        void write(Property<T> property, T newValue);
    }

    private T value;
    private Binding binding;
    private boolean evaluating;
    private WriteInterceptor<T> interceptor;
    private final List<Consumer<T>> valueListeners = new ArrayList<>();
    private final List<Runnable> invalidationListeners = new ArrayList<>();
    private final List<Runnable> bindingUnsubscribes = new ArrayList<>();

    public Property() {
        this(null);
    }

    public Property(T initial) {
        this.value = initial;
    }

    public T get() {
        BindingEvaluationContext.recordRead(this);
        return value;
    }

    public T peek() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public void set(T newValue) {
        if (newValue instanceof Binding) {
            bind((Binding) newValue);
            return;
        }
        clearBinding();
        if (interceptor != null) {
            interceptor.write(this, newValue);
        } else {
            setInternal(newValue);
        }
    }

    public void setBypassInterceptor(T newValue) {
        setInternal(newValue);
    }

    public void setInterceptor(WriteInterceptor<T> i) {
        this.interceptor = i;
    }

    public WriteInterceptor<T> interceptor() {
        return interceptor;
    }

    public void bind(Binding b) {
        clearBinding();
        this.binding = b;
        Deque<List<Property<?>>> st = deferStack.get();
        if (st != null && !st.isEmpty()) {
            st.peek().add(this);
            return;
        }
        reevaluate();
    }

    private static final ThreadLocal<Deque<List<Property<?>>>> deferStack = new ThreadLocal<>();

    public static void pushDeferred() {
        Deque<List<Property<?>>> st = deferStack.get();
        if (st == null) {
            st = new ArrayDeque<>();
            deferStack.set(st);
        }
        st.push(new ArrayList<>());
    }

    public static void flushDeferred() {
        Deque<List<Property<?>>> st = deferStack.get();
        if (st == null || st.isEmpty()) return;
        List<Property<?>> pending = st.pop();
        evaluatePending(pending);
    }

    public static void drainDeferred() {
        Deque<List<Property<?>>> st = deferStack.get();
        if (st == null || st.isEmpty()) return;
        List<Property<?>> pending = st.peek();
        if (pending.isEmpty()) return;
        List<Property<?>> snapshot = new ArrayList<>(pending);
        pending.clear();
        evaluatePending(snapshot);
    }

    private static void evaluatePending(List<Property<?>> pending) {
        for (Property<?> p : pending) {
            if (p.binding != null) p.reevaluate();
        }
    }

    public void unbind() {
        clearBinding();
    }

    public boolean isBound() {
        return binding != null;
    }

    public void addListener(Consumer<T> l) {
        valueListeners.add(l);
    }

    // Wire a QML on<Prop>Changed handler: Qt's change handlers take no args (they
    // read the property), so the new value is dropped.
    public void addChangeHandler(io.qml4j.engine.SignalHandler h) {
        valueListeners.add(v -> h.invoke(EMPTY_ARGS));
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    public void removeListener(Consumer<T> l) {
        valueListeners.remove(l);
    }

    public void addInvalidationListener(Runnable r) {
        invalidationListeners.add(r);
    }

    public void removeInvalidationListener(Runnable r) {
        invalidationListeners.remove(r);
    }

    private void setInternal(T newValue) {
        if (Objects.equals(value, newValue)) return;
        value = newValue;
        for (Runnable r : new ArrayList<>(invalidationListeners)) r.run();
        for (Consumer<T> l : new ArrayList<>(valueListeners)) l.accept(value);
    }

    private void clearBinding() {
        binding = null;
        for (Runnable r : bindingUnsubscribes) r.run();
        bindingUnsubscribes.clear();
    }

    @SuppressWarnings("unchecked")
    private void reevaluate() {
        if (binding == null) return;
        if (evaluating) {
            throw new IllegalStateException("cycle detected in binding evaluation");
        }
        evaluating = true;
        try {
            Set<Property<?>> reads = new LinkedHashSet<>();
            for (Runnable r : bindingUnsubscribes) r.run();
            bindingUnsubscribes.clear();

            Object result;
            BindingEvaluationContext.push(reads);
            try {
                result = binding.evaluate();
            } finally {
                BindingEvaluationContext.pop();
            }

            Runnable reeval = this::reevaluate;
            Runnable invalidate = () -> {
                DirtyQueue dq = DirtyQueue.current();
                if (dq != null) dq.enqueue(reeval);
                else reeval.run();
            };
            for (Property<?> dep : reads) {
                if (dep == this) continue;
                dep.addInvalidationListener(invalidate);
                final Property<?> capturedDep = dep;
                bindingUnsubscribes.add(() -> capturedDep.removeInvalidationListener(invalidate));
            }
            if (interceptor != null) {
                interceptor.write(this, (T) result);
            } else {
                setInternal((T) result);
            }
        } finally {
            evaluating = false;
        }
    }
}
