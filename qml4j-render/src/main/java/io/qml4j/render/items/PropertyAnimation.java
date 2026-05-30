package io.qml4j.render.items;

import io.qml4j.engine.RuntimeHelpers;
import io.qml4j.engine.binding.Property;

public class PropertyAnimation extends Item implements Animatable {
    public final Property<Object> target = new Property<>(null);
    public final Property<String> property = new Property<>(null);
    public final Property<String> properties = new Property<>(null);
    public final Property<Object> from = new Property<>(null);
    public final Property<Object> to = new Property<>(null);
    public final Property<Number> duration = new Property<>(250);
    public final Property<Boolean> running = new Property<>(Boolean.FALSE);
    public final Property<String> easing = new Property<>("linear");

    public boolean ephemeral;

    private long startNanos = -1L;
    private boolean prepared;
    protected Object preparedFrom;
    protected Object preparedTo;

    public PropertyAnimation() {
        visible.set(Boolean.FALSE);
    }

    @Override
    public void tick(long nowNanos) {
        if (!Boolean.TRUE.equals(running.peek())) {
            reset();
            return;
        }
        Object t = target.peek();
        String prop = effectiveProperty();
        if (t == null || prop == null) return;

        if (!prepared) prepare();
        if (startNanos < 0L) startNanos = nowNanos;
        double durMs = duration.peek().doubleValue();
        if (durMs <= 0) {
            RuntimeHelpers.writeMember(t, prop, preparedTo);
            stop();
            return;
        }
        double frac = (nowNanos - startNanos) / 1_000_000.0 / durMs;
        boolean done = frac >= 1.0;
        if (done) frac = 1.0;
        double eased = Easings.apply(easing.peek(), frac);
        RuntimeHelpers.writeMember(t, prop, interpolate(preparedFrom, preparedTo, eased));
        if (done) stop();
    }

    protected String effectiveProperty() {
        return property.peek();
    }

    private void prepare() {
        preparedFrom = coerceFrom(from.peek());
        preparedTo = coerceTo(to.peek());
        onPrepared();
        prepared = true;
    }

    private void reset() {
        startNanos = -1L;
        prepared = false;
    }

    private void stop() {
        running.set(Boolean.FALSE);
        reset();
    }

    protected Object coerceFrom(Object raw) { return raw; }
    protected Object coerceTo(Object raw) { return raw; }
    protected void onPrepared() {}

    public boolean acceptsTransition(Object before, Object after) {
        return before instanceof Number && after instanceof Number;
    }

    protected Object interpolate(Object fromV, Object toV, double t) {
        if (fromV instanceof Number && toV instanceof Number) {
            double a = ((Number) fromV).doubleValue();
            double b = ((Number) toV).doubleValue();
            return a + (b - a) * t;
        }
        return t >= 1.0 ? toV : fromV;
    }
}
