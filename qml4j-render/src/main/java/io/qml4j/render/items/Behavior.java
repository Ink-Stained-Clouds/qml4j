package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

import java.lang.reflect.Field;

public class Behavior extends Item implements Animatable {

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
        raw.addListener(this::onChanged);
    }

    private void readTemplate() {
        for (Item c : children) {
            if (!(c instanceof NumberAnimation)) continue;
            NumberAnimation na = (NumberAnimation) c;
            Number d = na.duration.peek();
            if (d != null) durationMs = d.longValue();
            String e = na.easing.peek();
            if (e != null) easing = e;
            return;
        }
    }

    private void onChanged(Object newVal) {
        if (writing || bound == null) return;
        if (!(newVal instanceof Number) || !(lastDisplayed instanceof Number)) {
            lastDisplayed = newVal;
            return;
        }
        double target = ((Number) newVal).doubleValue();
        double current = ((Number) lastDisplayed).doubleValue();
        if (!running && target == current) return;
        from = current;
        to = target;
        running = true;
        startNanos = -1L;
        writeBack(current);
    }

    @Override
    public void tick(long nowNanos) {
        if (!running || bound == null) return;
        if (startNanos < 0L) startNanos = nowNanos;
        double frac = durationMs <= 0
            ? 1.0
            : Math.min(1.0, (nowNanos - startNanos) / 1_000_000.0 / durationMs);
        double v = from + (to - from) * Easings.apply(easing, frac);
        writeBack(v);
        if (frac >= 1.0) {
            running = false;
            startNanos = -1L;
        }
    }

    private void writeBack(double v) {
        Number out = wantsLong ? (Number) Long.valueOf(Math.round(v)) : (Number) Double.valueOf(v);
        writing = true;
        try {
            bound.set(out);
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
