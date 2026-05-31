package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public abstract class AbstractAnimation extends Item implements Animatable {
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);

    protected AbstractAnimation() {
        visible.set(Boolean.FALSE);
    }

    public void start() { running.set(Boolean.TRUE); }
    public void stop() { running.set(Boolean.FALSE); }
    public void restart() { stop(); start(); }
}
