package io.qml4j.render.items.animation;
import io.qml4j.runtime.member.MemberAccess;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

public class PropertyAnimation extends AbstractAnimation {
    public final Property<Object> target = new Property<>(null);
    public final Property<String> property = new Property<>(null);
    public final Property<String> properties = new Property<>(null);
    public final Property<Object> from = new Property<>(null);
    public final Property<Object> to = new Property<>(null);
    public final Property<Number> duration = new Property<>(250);
    public final Easing easing = new Easing();

    public boolean ephemeral;

    private long startNanos = -1L;
    private boolean prepared;
    private int loopsDone;
    protected Object preparedFrom;
    protected Object preparedTo;

    // `NumberAnimation on x { ... }` -- an animation attached directly to a property
    // (the animation-as-Behavior shorthand). Targets owner.prop and runs immediately.
    public void attach(Object owner, String prop) {
        target.set(owner);
        property.set(prop);
        start();
    }

    @Override
    public void start() {
        // Qt semantics: starting a property animation takes exclusive control of
        // its (target, property). Stop any other animation still driving the same
        // property so the newcomer wins instead of the two writing it each frame
        // (e.g. a ripple's fade-out must override its still-running fade-in).
        Object t = target.peek();
        String prop = effectiveProperty();
        if (t != null && prop != null) {
            Item top = this;
            while (top.parent.peek() != null) top = top.parent.peek();
            stopConflicting(top, t, prop);
        }
        super.start();
    }

    private void stopConflicting(Item node, Object t, String prop) {
        if (node != this && node instanceof PropertyAnimation) {
            PropertyAnimation pa = (PropertyAnimation) node;
            if (Boolean.TRUE.equals(pa.running.peek())
                    && pa.target.peek() == t
                    && prop.equals(pa.effectiveProperty())) {
                pa.stop();
            }
        }
        for (Item c : node.children) stopConflicting(c, t, prop);
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

        if (!prepared) prepare(t, prop);
        if (startNanos < 0L) startNanos = nowNanos;
        double durMs = duration.peekDouble();
        if (durMs <= 0) {
            MemberAccess.writeMember(t, prop, preparedTo);
            stop();
            finished.emit();
            return;
        }
        double frac = (nowNanos - startNanos) / 1_000_000.0 / durMs;
        boolean done = frac >= 1.0;
        if (done) frac = 1.0;
        double eased = Easings.apply(easing.type.peekInt(), frac);
        MemberAccess.writeMember(t, prop, interpolate(preparedFrom, preparedTo, eased));
        if (done) {
            int total = loops.peekInt(); // Animation.Infinite (-1) loops forever
            loopsDone++;
            if (total >= 0 && loopsDone >= total) {
                stop();
                finished.emit();
            } else {
                startNanos = -1L; // replay from the start on the next tick (from/to kept)
            }
        }
    }

    protected String effectiveProperty() {
        return property.peek();
    }

    private void prepare(Object target, String prop) {
        // Qt: an omitted `from` defaults to the property's current value.
        Object rawFrom = from.peek();
        if (rawFrom == null) rawFrom = MemberAccess.readMember(target, prop);
        preparedFrom = coerceFrom(rawFrom);
        preparedTo = coerceTo(to.peek());
        onPrepared();
        prepared = true;
    }

    private void reset() {
        startNanos = -1L;
        prepared = false;
        loopsDone = 0;
    }

    @Override
    public void stop() {
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
