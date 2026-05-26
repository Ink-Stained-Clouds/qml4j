package io.qml4j.engine;

import java.util.Iterator;
import java.util.LinkedHashSet;

public final class DirtyQueue {

    private final LinkedHashSet<Runnable> items = new LinkedHashSet<>();

    public void enqueue(Runnable r) {
        items.add(r);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void flush() {
        while (!items.isEmpty()) {
            Iterator<Runnable> it = items.iterator();
            Runnable r = it.next();
            it.remove();
            r.run();
        }
    }
}
