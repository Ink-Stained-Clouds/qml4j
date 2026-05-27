package io.qml4j.render.items;

import io.qml4j.engine.RuntimeHelpers;
import io.qml4j.engine.binding.Property;

public class NumberAnimation extends Item {
    public final Property<Object> target = new Property<>(null);
    public final Property<String> property = new Property<>(null);
    public final Property<String> properties = new Property<>(null);
    public final Property<Number> from = new Property<>(0);
    public final Property<Number> to = new Property<>(0);
    public final Property<Number> duration = new Property<>(250);
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);
    public final Property<String> easing = new Property<>("linear");

    public boolean ephemeral;

    private long startNanos = -1L;

    public NumberAnimation() {
        visible.set(Boolean.FALSE);
    }

    public void tick(long nowNanos) {
        if (!Boolean.TRUE.equals(running.peek())) {
            startNanos = -1L;
            return;
        }
        Object t = target.peek();
        String prop = property.peek();
        if (t == null || prop == null) return;

        if (startNanos < 0L) startNanos = nowNanos;
        double durMs = duration.peek().doubleValue();
        if (durMs <= 0) {
            RuntimeHelpers.writeMember(t, prop, to.peek());
            running.set(Boolean.FALSE);
            startNanos = -1L;
            return;
        }
        double elapsedMs = (nowNanos - startNanos) / 1_000_000.0;
        double frac = elapsedMs / durMs;
        boolean done = frac >= 1.0;
        if (done) frac = 1.0;
        double eased = ease(easing.peek(), frac);
        double a = from.peek().doubleValue();
        double b = to.peek().doubleValue();
        double v = a + (b - a) * eased;
        RuntimeHelpers.writeMember(t, prop, v);
        if (done) {
            running.set(Boolean.FALSE);
            startNanos = -1L;
        }
    }

    private static double ease(String name, double t) {
        if (name == null || "linear".equals(name)) return t;
        switch (name) {
            case "easeInQuad":  return t * t;
            case "easeOutQuad": return 1.0 - (1.0 - t) * (1.0 - t);
            case "easeInOutQuad":
                return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            default: return t;
        }
    }
}
