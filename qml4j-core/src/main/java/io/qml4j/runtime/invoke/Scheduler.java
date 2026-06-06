package io.qml4j.runtime.invoke;

import io.qml4j.engine.Callable;
import io.qml4j.engine.binding.Binding;
import io.qml4j.engine.binding.DirtyQueue;

// Qt.callLater: defer work to the end of the current dirty-queue flush, or run
// it immediately when there is no active queue.
public final class Scheduler {

    private Scheduler() {}

    private static final Object[] EMPTY_ARGS = new Object[0];

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
