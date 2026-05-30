package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

import java.lang.reflect.Field;
import java.util.Objects;

public class Behavior extends Item implements Animatable, Property.WriteInterceptor<Object> {

    private Property<Object> bound;
    private boolean wantsLong;
    private long durationMs = 250L;
    private String easing = "linear";

    private boolean running;
    private long startNanos = -1L;
    private double from;
    private double to;
    private Object lastDisplayed;
    private boolean writing;

    public Behavior() {
        visible.set(Boolean.FALSE);
    }

    public void attach(Object owner, String propName) {
        Property<?> p = findProperty(owner, propName);
        if (p == null) return;
        @SuppressWarnings({"rawtypes", "unchecked"})
        Property<Object> raw = (Property) p;
        bound = raw;
        lastDisplayed = raw.peek();
        wantsLong = lastDisplayed instanceof Long || lastDisplayed instanceof Integer;
        readTemplate();
        raw.setInterceptor(this);
    }

    private void readTemplate() {
        for (Item c : children) {
            if (!(c instanceof PropertyAnimation)) continue;
            PropertyAnimation na = (PropertyAnimation) c;
            Number d = na.duration.peek();
            if (d != null) durationMs = d.longValue();
            String e = na.easing.peek();
            if (e != null) easing = e;
            return;
        }
    }

    @Override
    public void write(Property<Object> property, Object newValue) {
        if (writing) {
            property.setBypassInterceptor(newValue);
            return;
        }
        if (!(newValue instanceof Number) || !(lastDisplayed instanceof Number)) {
            writeBack(newValue);
            return;
        }
        double target = ((Number) newValue).doubleValue();
        double current = ((Number) lastDisplayed).doubleValue();
        if (!running && target == current) return;
        from = current;
        to = target;
        running = true;
        startNanos = -1L;
        writeBack(coerce(current));
    }

    @Override
    public void tick(long nowNanos) {
        if (!running || bound == null) return;
        if (startNanos < 0L) startNanos = nowNanos;
        double frac = durationMs <= 0
            ? 1.0
            : Math.min(1.0, (nowNanos - startNanos) / 1_000_000.0 / durationMs);
        double v = from + (to - from) * Easings.apply(easing, frac);
        writeBack(coerce(v));
        if (frac >= 1.0) {
            running = false;
            startNanos = -1L;
        }
    }

    private Object coerce(double v) {
        return wantsLong ? (Object) Long.valueOf(Math.round(v)) : (Object) Double.valueOf(v);
    }

    private void writeBack(Object out) {
        if (Objects.equals(lastDisplayed, out)) return;
        writing = true;
        try {
            bound.setBypassInterceptor(out);
        } finally {
            writing = false;
        }
        lastDisplayed = out;
    }

    private static Property<?> findProperty(Object obj, String name) {
        try {
            Field f = obj.getClass().getField(name);
            if (Property.class.isAssignableFrom(f.getType())) {
                return (Property<?>) f.get(obj);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
        }
        return null;
    }
}
