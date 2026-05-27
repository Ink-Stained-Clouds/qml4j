package io.qml4j.engine;

import java.util.Iterator;
import java.util.LinkedHashSet;

public final class DirtyQueue {

    private static final ThreadLocal<DirtyQueue> CURRENT = new ThreadLocal<>();

    private final LinkedHashSet<Runnable> items = new LinkedHashSet<>();

    public static DirtyQueue current() {
        return CURRENT.get();
    }

    public void install() {
        CURRENT.set(this);
    }

    public void uninstall() {
        if (CURRENT.get() == this) CURRENT.remove();
    }

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
