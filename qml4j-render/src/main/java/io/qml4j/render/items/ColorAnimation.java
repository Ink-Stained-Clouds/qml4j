package io.qml4j.render.items;

public class ColorAnimation extends PropertyAnimation {

    @Override
    public boolean acceptsTransition(Object before, Object after) {
        return isColorish(before) && isColorish(after);
    }

    private static boolean isColorish(Object v) {
        return v instanceof String || v instanceof Number;
    }

    @Override
    protected Object coerceFrom(Object raw) { return ColorMath.parse(raw); }

    @Override
    protected Object coerceTo(Object raw) { return ColorMath.parse(raw); }

    @Override
    protected Object interpolate(Object fromV, Object toV, double t) {
        if (!(fromV instanceof Integer) || !(toV instanceof Integer)) {
            return t >= 1.0 ? toV : fromV;
        }
        int blended = ColorMath.lerpHsv((Integer) fromV, (Integer) toV, t);
        return ColorMath.formatHex(blended);
    }
}
