package io.qml4j.engine.binding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class Property<T> {

    private T value;
    private Binding binding;
    private boolean evaluating;
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

    public void set(T newValue) {
        clearBinding();
        setInternal(newValue);
    }

    public void bind(Binding b) {
        clearBinding();
        this.binding = b;
        reevaluate();
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
            setInternal((T) result);
        } finally {
            evaluating = false;
        }
    }
}
