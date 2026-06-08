package io.github.timer_err.qml4j.render.items.animation;

import io.github.timer_err.qml4j.engine.binding.Property;

public class RotationAnimation extends PropertyAnimation {
    // Object, not String: QML may assign the enum (RotationAnimation.Shortest -> a Long)
    // or the string form ("Shortest"); normalised via dirName in onPrepared.
    public final Property<Object> direction = new Property<>("Numerical");

    @Override
    protected void onPrepared() {
        if (!(preparedFrom instanceof Number) || !(preparedTo instanceof Number)) return;
        double f = ((Number) preparedFrom).doubleValue();
        double t = ((Number) preparedTo).doubleValue();
        String dir = dirName(direction.peek());
        if ("Shortest".equals(dir)) {
            double d = ((t - f) % 360.0 + 540.0) % 360.0 - 180.0;
            preparedTo = f + d;
        } else if ("Clockwise".equals(dir)) {
            while (t < f) t += 360.0;
            preparedTo = t;
        } else if ("Counterclockwise".equals(dir)) {
            while (t > f) t -= 360.0;
            preparedTo = t;
        }
    }

    // The string direction name from either the string form or the enum ordinal
    // (RotationAnimation.Numerical/Shortest/Clockwise/Counterclockwise -> 0/1/2/3).
    private static String dirName(Object d) {
        if (d instanceof Number) {
            switch (((Number) d).intValue()) {
                case 1: return "Shortest";
                case 2: return "Clockwise";
                case 3: return "Counterclockwise";
                default: return "Numerical";
            }
        }
        return d == null ? "Numerical" : d.toString();
    }
}
