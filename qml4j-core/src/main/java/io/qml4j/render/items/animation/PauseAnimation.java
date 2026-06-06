package io.qml4j.render.items.animation;

import io.qml4j.engine.binding.Property;

public class PauseAnimation extends AbstractAnimation {
    public final Property<Number> duration = new Property<>(250);

    private long startNanos = -1L;

    @Override
    public void tick(long nowNanos) {
        if (!Boolean.TRUE.equals(running.peek())) {
            startNanos = -1L;
            return;
        }
        if (startNanos < 0L) startNanos = nowNanos;
        double durMs = duration.peekDouble();
        if (durMs <= 0) {
            running.set(Boolean.FALSE);
            startNanos = -1L;
            return;
        }
        double elapsedMs = (nowNanos - startNanos) / 1_000_000.0;
        if (elapsedMs >= durMs) {
            running.set(Boolean.FALSE);
            startNanos = -1L;
        }
    }
}
