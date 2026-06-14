package io.github.timer_err.qml4j.runtime.invoke;

import io.github.timer_err.qml4j.engine.Callable;
import io.github.timer_err.qml4j.engine.SignalHandler;
import io.github.timer_err.qml4j.engine.binding.Binding;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;

// Qt.callLater: defer work to the end of the current dirty-queue flush, or run
// it immediately when there is no active queue.
public final class Scheduler {

    private Scheduler() {}

    private static final Object[] EMPTY_ARGS = new Object[0];

    // Component.onCompleted: run the handler once after the current construction +
    // binding flush settles (end of the dirty-queue flush), or now if no queue is
    // active.
    @SuppressWarnings("unused")
    public static void runLater(SignalHandler h) {
        if (h == null) return;
        Runnable r = () -> h.invoke(EMPTY_ARGS);
        // During construction a deferred binding batch is active; onCompleted must run
        // after it flushes so handlers see final bound values (e.g. a property bound to a
        // context property). Outside construction, defer to the dirty-queue flush, or now.
        if (Property.hasDeferredBatch()) {
            Property.runAfterFlush(r);
            return;
        }
        DirtyQueue dq = DirtyQueue.current();
        if (dq != null) dq.enqueue(r);
        else r.run();
    }

    public static void qtCallLater(Object work) {
        if (work == null) return;
        Runnable r = toRunnable(work);
        DirtyQueue dq = DirtyQueue.current();
        if (dq != null) dq.enqueue(r);
        else r.run();
    }

    private static Runnable toRunnable(Object work) {
        if (work instanceof Runnable) return (Runnable) work;
        if (work instanceof Binding) {
            Binding b = (Binding) work;
            return b::evaluate;
        }
        if (work instanceof Callable) {
            Callable c = (Callable) work;
            return () -> c.call(EMPTY_ARGS);
        }
        throw new IllegalArgumentException(
            "Qt.callLater requires Qt.binding(...), an arrow function, or a callable; got "
            + work.getClass().getName());
    }
}
