package io.qml4j.render.items.animation;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.ParentTransparent;
import io.qml4j.engine.Signal;
import io.qml4j.engine.binding.Property;

public abstract class AbstractAnimation extends Item implements Animatable, ParentTransparent {
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);
    public final Property<Number> loops = new Property<>(1); // Animation.Infinite (-1) loops forever
    // Emitted only on natural completion (reaching the end), not on manual stop()
    // -- matches Qt's Animation.finished vs stopped distinction.
    public final Signal finished = new Signal();

    protected AbstractAnimation() {
        visible.set(Boolean.FALSE);
    }

    public void start() { running.set(Boolean.TRUE); }
    public void stop() { running.set(Boolean.FALSE); }
    public void restart() { stop(); start(); }

    // The `parent` keyword used in a binding inside this animation: climb out of this and
    // any enclosing animation groups to the nearest visual item, and resolve to ITS parent
    // (Qt skips the non-Item animations entirely). The final read records the dependency.
    @Override
    public Object qmlParent() {
        Item it = this;
        while (it instanceof ParentTransparent) {
            Item p = it.parent.peek();
            if (p == null) return null;
            it = p;
        }
        return it.parent.get();
    }
}
