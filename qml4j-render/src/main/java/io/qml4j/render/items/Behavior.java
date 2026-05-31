package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

import java.lang.reflect.Field;
import java.util.Objects;

public class Behavior extends Item implements Animatable, Property.WriteInterceptor<Object> {

    private Property<Object> bound;
    private PropertyAnimation template;
    private long durationMs = 250L;
    private int easingType = 0;

    private boolean running;
    private long startNanos = -1L;
    private Object preparedFrom;
    private Object preparedTo;
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
        readTemplate();
        raw.setInterceptor(this);
    }

    private void readTemplate() {
        for (Item c : children) {
            if (!(c instanceof PropertyAnimation)) continue;
            template = (PropertyAnimation) c;
            Number d = template.duration.peek();
            if (d != null) durationMs = d.longValue();
            Number e = template.easing.type.peek();
            if (e != null) easingType = e.intValue();
            return;
        }
    }

    @Override
    public void write(Property<Object> property, Object newValue) {
        if (writing) {
            property.setBypassInterceptor(newValue);
            return;
        }
        if (!canTween(lastDisplayed, newValue)) {
            writeBack(newValue);
            return;
        }
        Object pFrom = template.coerceFrom(lastDisplayed);
        Object pTo = template.coerceTo(newValue);
        template.preparedFrom = pFrom;
        template.preparedTo = pTo;
        template.onPrepared();
        pFrom = template.preparedFrom;
        pTo = template.preparedTo;
        if (!running && Objects.equals(pFrom, pTo)) return;
        preparedFrom = pFrom;
        preparedTo = pTo;
        running = true;
        startNanos = -1L;
        writeBack(template.interpolate(pFrom, pTo, 0.0));
    }

    @Override
    public void tick(long nowNanos) {
        if (!running || bound == null) return;
        if (startNanos < 0L) startNanos = nowNanos;
        double frac = durationMs <= 0
            ? 1.0
            : Math.min(1.0, (nowNanos - startNanos) / 1_000_000.0 / durationMs);
        double eased = Easings.apply(easingType, frac);
        Object out = template != null
            ? template.interpolate(preparedFrom, preparedTo, eased)
            : preparedTo;
        writeBack(out);
        if (frac >= 1.0) {
            running = false;
            startNanos = -1L;
        }
    }

    private boolean canTween(Object before, Object after) {
        if (template == null) return false;
        return template.acceptsTransition(before, after);
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
