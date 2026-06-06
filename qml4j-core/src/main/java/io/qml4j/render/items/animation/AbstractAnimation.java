package io.qml4j.render.items.animation;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.Signal;
import io.qml4j.engine.binding.Property;

public abstract class AbstractAnimation extends Item implements Animatable {
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);
    // Emitted only on natural completion (reaching the end), not on manual stop()
    // -- matches Qt's Animation.finished vs stopped distinction.
    public final Signal finished = new Signal();

    protected AbstractAnimation() {
        visible.set(Boolean.FALSE);
    }

    public void start() { running.set(Boolean.TRUE); }
    public void stop() { running.set(Boolean.FALSE); }
    public void restart() { stop(); start(); }
}
