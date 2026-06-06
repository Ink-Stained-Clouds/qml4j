package io.qml4j.render.items.animation;

import io.qml4j.engine.binding.Property;

public class RotationAnimation extends PropertyAnimation {
    public final Property<String> direction = new Property<>("Numerical");

    @Override
    protected void onPrepared() {
        if (!(preparedFrom instanceof Number) || !(preparedTo instanceof Number)) return;
        double f = ((Number) preparedFrom).doubleValue();
        double t = ((Number) preparedTo).doubleValue();
        String dir = direction.peek();
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
}
