package io.github.timer_err.qml4j.render.items.animation;

import io.github.timer_err.qml4j.engine.Signal;

public class ScriptAction extends AbstractAnimation {
    public final Signal trigger = new Signal();

    @Override
    public void tick(long nowNanos) {
        if (!Boolean.TRUE.equals(running.peek())) return;
        trigger.emit();
        running.set(Boolean.FALSE);
    }
}
