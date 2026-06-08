package io.github.timer_err.qml4j.render.items.animation;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;

public class Timer extends Item implements Animatable {
    public final Property<Number> interval = new Property<>(1000);
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);
    public final Property<Boolean> repeat = new Property<>(Boolean.FALSE);
    public final Property<Boolean> triggeredOnStart = new Property<>(Boolean.FALSE);
    public final Signal triggered = new Signal();

    private long nextFireNanos = -1;

    public Timer() {
        running.addListener(v -> {
            if (!Boolean.TRUE.equals(v)) nextFireNanos = -1;
            else if (nextFireNanos < 0) armed = true;
        });
    }

    private boolean armed = false;

    public void start() { running.set(Boolean.TRUE); }
    public void stop() { running.set(Boolean.FALSE); }
    public void restart() { stop(); start(); }

    @Override
    public void tick(long nowNanos) {
        if (!Boolean.TRUE.equals(running.peek())) return;
        long intervalNanos = (long) (interval.peekDouble() * 1_000_000d);
        if (intervalNanos <= 0) return;
        if (armed) {
            armed = false;
            if (Boolean.TRUE.equals(triggeredOnStart.peek())) {
                triggered.emit();
                if (!Boolean.TRUE.equals(repeat.peek())) {
                    running.set(Boolean.FALSE);
                    return;
                }
            }
            nextFireNanos = nowNanos + intervalNanos;
            return;
        }
        if (nowNanos >= nextFireNanos) {
            triggered.emit();
            if (!Boolean.TRUE.equals(repeat.peek())) {
                running.set(Boolean.FALSE);
                nextFireNanos = -1;
            } else {
                nextFireNanos += intervalNanos;
                if (nextFireNanos <= nowNanos) nextFireNanos = nowNanos + intervalNanos;
            }
        }
    }
}
