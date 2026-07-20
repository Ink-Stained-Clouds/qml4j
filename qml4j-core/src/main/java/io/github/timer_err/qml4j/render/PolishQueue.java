package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.core.Item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// Per-view layout-invalidation queue -- the "polish list" borrowed from Qt Quick. An Item
// whose layout-affecting geometry changes marks itself dirty and enqueues here; the renderer
// drains this instead of re-measuring the whole tree every frame.
//
// Unlike DirtyQueue (installed per-frame for binding re-eval), a PolishQueue is installed for
// the owning QmlView's lifetime, because properties are also mutated BETWEEN frames (input
// dispatch, host-driven setters). Those marks must survive to be drained by the next settle.
// Single-threaded engine: one current queue per thread (last-installed view wins).
public final class PolishQueue {

    private static final ThreadLocal<PolishQueue> CURRENT = new ThreadLocal<>();

    // Insertion-ordered so the drain tends to process bottom-up (a child enqueues before the
    // parent it propagates to), which helps derived-size chains converge in fewer passes.
    private final LinkedHashSet<Item> items = new LinkedHashSet<>();

    public static PolishQueue current() {
        return CURRENT.get();
    }

    public void install() {
        CURRENT.set(this);
    }

    public void uninstall() {
        if (CURRENT.get() == this) CURRENT.remove();
    }

    // Add an item to the polish list. The Item's own dirty bits dedupe re-adds, so this set
    // never grows unbounded from repeated marks on the same item within a frame.
    public void enqueue(Item item) {
        items.add(item);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
    }

    // Snapshot + clear the current contents for one drain pass. The caller processes the
    // snapshot; work done while processing may enqueue more items for the next pass.
    public List<Item> drainSnapshot() {
        List<Item> snap = new ArrayList<>(items);
        items.clear();
        return snap;
    }
}
