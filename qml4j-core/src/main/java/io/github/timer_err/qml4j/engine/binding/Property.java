package io.github.timer_err.qml4j.engine.binding;

import io.github.timer_err.qml4j.engine.SignalHandler;

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

    // Null-safe numeric reads: a binding that evaluated to undefined leaves a null
    // value, which QML treats as 0 for a numeric use (instead of NPEing the renderer).
    public float peekFloat() {
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    public double peekDouble() {
        return value instanceof Number ? ((Number) value).doubleValue() : 0d;
    }

    public int peekInt() {
        return value instanceof Number ? ((Number) value).intValue() : 0;
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

    // Set without bumping the global change-version (but still notify dependents).
    // For paint-only state -- a Flickable's contentX/Y, which the renderer applies
    // as a draw-time translate and which affects no item's measured geometry. A pure
    // scroll frame thus keeps the idle layout-skip fast path: dependents (a windowed
    // list's `first` binding) still re-evaluate and, if they actually change, bump
    // the version themselves so the needed relayout still happens.
    public void setPaintOnly(T newValue) {
        if (unchanged(value, newValue)) return;
        value = newValue;
        fireListeners();
    }

    // Notify invalidation + value listeners. The overwhelming majority of properties
    // carry 0 or 1 listener of each kind (a single binding / a single Changed signal),
    // so fast-path those without the defensive ArrayList copy -- that copy ran on every
    // set in the whole scene (every scroll frame, every animation tick, the 5 Hz play
    // clock) and was pure churn. Only a list with >1 listener can be structurally
    // mutated by one of its own callbacks mid-iteration, so only that case is copied.
    private void fireListeners() {
        int ni = invalidationListeners.size();
        if (ni == 1) {
            invalidationListeners.get(0).run();
        } else if (ni > 1) {
            for (Runnable r : new ArrayList<>(invalidationListeners)) r.run();
        }
        int nv = valueListeners.size();
        if (nv == 1) {
            valueListeners.get(0).accept(value);
        } else if (nv > 1) {
            for (Consumer<T> l : new ArrayList<>(valueListeners)) l.accept(value);
        }
    }

    // A change that originates from the widget itself (the user typing into a TextInput),
    // not a QML imperative assignment. Unlike set(), it PRESERVES any binding so a two-way
    // `text: control.text` + `onTextChanged: control.text = text` pattern keeps working: the
    // keystroke updates the value and notifies (the handler writes back to the source), and a
    // later external change to the source still propagates through the still-live binding --
    // else a clear button that sets the source has no effect once the user has typed once.
    public void setFromEdit(T newValue) {
        if (interceptor != null) interceptor.write(this, newValue);
        else setInternal(newValue);
    }

    public void setInterceptor(WriteInterceptor<T> i) {
        this.interceptor = i;
    }

    @SuppressWarnings("unused")
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
    // Callbacks (Component.onCompleted handlers) to run after the current deferred batch's
    // bindings evaluate -- so a handler reading a deferred-bound property sees its final
    // value, not the construction-time default. One list per batch, mirroring deferStack.
    private static final ThreadLocal<Deque<List<Runnable>>> afterFlushStack = new ThreadLocal<>();

    @SuppressWarnings("unused")
    public static void pushDeferred() {
        Deque<List<Property<?>>> st = deferStack.get();
        if (st == null) {
            st = new ArrayDeque<>();
            deferStack.set(st);
        }
        st.push(new ArrayList<>());
        Deque<List<Runnable>> as = afterFlushStack.get();
        if (as == null) {
            as = new ArrayDeque<>();
            afterFlushStack.set(as);
        }
        as.push(new ArrayList<>());
    }

    @SuppressWarnings("unused")
    public static void flushDeferred() {
        Deque<List<Property<?>>> st = deferStack.get();
        if (st == null || st.isEmpty()) return;
        List<Property<?>> pending = st.pop();
        evaluatePending(pending);
        Deque<List<Runnable>> as = afterFlushStack.get();
        if (as != null && !as.isEmpty()) {
            for (Runnable r : as.pop()) r.run();
        }
    }

    public static boolean hasDeferredBatch() {
        Deque<List<Property<?>>> st = deferStack.get();
        return st != null && !st.isEmpty();
    }

    // Run `r` after the current deferred batch flushes (Component.onCompleted ordering),
    // or immediately when no batch is active.
    public static void runAfterFlush(Runnable r) {
        Deque<List<Runnable>> as = afterFlushStack.get();
        if (as != null && !as.isEmpty()) {
            as.peek().add(r);
        } else {
            r.run();
        }
    }

    @SuppressWarnings("unused")
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
    public void addChangeHandler(SignalHandler h) {
        valueListeners.add(v -> h.invoke(EMPTY_ARGS));
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    @SuppressWarnings("unused")
    public void removeListener(Consumer<T> l) {
        valueListeners.remove(l);
    }

    // Qt's implicit per-property <prop>Changed() signal, emitted manually from QML to
    // force dependents to re-read after an in-place mutation the setter never saw (a
    // `var` object whose fields changed but whose reference stayed equal). Re-runs the
    // same listeners a real value change would, without touching the value.
    public void notifyChanged() {
        changeVersion++;
        fireListeners();
    }

    public void addInvalidationListener(Runnable r) {
        invalidationListeners.add(r);
    }

    public void removeInvalidationListener(Runnable r) {
        invalidationListeners.remove(r);
    }

    // Bumped on every actual property change in the scene, so the renderer can detect a
    // frame where nothing changed (idle UI) and skip the layout pass entirely -- the key to
    // embedding the engine over a game loop that calls renderFrame at full rate.
    private static long changeVersion;

    public static long changeVersion() {
        return changeVersion;
    }

    private void setInternal(T newValue) {
        if (unchanged(value, newValue)) return;
        changeVersion++;
        value = newValue;
        fireListeners();
    }

    // A value-change check that compares numbers by magnitude, not boxed identity: a
    // layout/binding re-delivering the same number in a different box (Double 712.0 vs
    // Long 712 vs Float 712.0f) is NOT a change. Objects.equals is type-strict, so without
    // this such re-deliveries fire invalidation forever -- a 1-binding layout oscillation
    // that never settles (ScrollBar implicitHeight: parent.height). NaN equals NaN here so
    // an unset numeric (NaN) re-delivery also doesn't churn.
    private static boolean unchanged(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            return da == db || (Double.isNaN(da) && Double.isNaN(db));
        }
        return Objects.equals(a, b);
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
