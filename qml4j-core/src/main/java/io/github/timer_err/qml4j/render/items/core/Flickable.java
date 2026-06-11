package io.github.timer_err.qml4j.render.items.core;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.animation.Animatable;

public class Flickable extends Item implements Animatable {
    public final Property<Number> contentX = new Property<>(0);
    public final Property<Number> contentY = new Property<>(0);
    public final Property<Number> contentWidth = new Property<>(0);
    public final Property<Number> contentHeight = new Property<>(0);
    public final Property<String> flickableDirection = new Property<>("AutoFlickDirection");
    public final Property<Boolean> interactive = new Property<>(Boolean.TRUE);
    public final Property<Boolean> moving = new Property<>(Boolean.FALSE);
    public final Property<Number> boundsBehavior = new Property<>(3); // DragAndOvershootBounds
    // Content margins (Qt Flickable.topMargin/...): accepted so documents load; the
    // content is laid out by its own anchors/size here, so these are not yet applied.
    public final Property<Number> topMargin = new Property<>(0);
    public final Property<Number> bottomMargin = new Property<>(0);
    public final Property<Number> leftMargin = new Property<>(0);
    public final Property<Number> rightMargin = new Property<>(0);
    // Touch press-delay before the flick steals the press from children; accepted so
    // documents load (no delayed-press synthesis here).
    public final Property<Number> pressDelay = new Property<>(0);

    // --- Flick / inertia -------------------------------------------------
    // Velocity (content px/sec) after the finger releases; decays under DECEL
    // until it falls below MIN_FLING or the content hits an edge. Stepped each
    // frame by tick() (the render loop walks Animatable nodes).
    private float flingVX;
    private float flingVY;
    private boolean flinging;
    private long flingLastNanos;
    private static final float DECEL = 2200f;     // px/s^2
    private static final float MIN_FLING = 50f;   // px/s

    /** Begin coasting with the given release velocity (content px/sec). */
    public void startFling(float vx, float vy) {
        flingVX = vx;
        flingVY = vy;
        flinging = Math.abs(vx) > MIN_FLING || Math.abs(vy) > MIN_FLING;
        flingLastNanos = 0L;
        moving.set(flinging);
    }

    /** Cancel any coast (a new touch landed on the flickable). */
    public void stopFling() {
        flinging = false;
    }

    @Override
    public void tick(long nowNanos) {
        if (!flinging) return;
        if (flingLastNanos == 0L) {
            flingLastNanos = nowNanos;
            return;
        }
        float dt = (nowNanos - flingLastNanos) / 1_000_000_000f;
        flingLastNanos = nowNanos;
        if (dt <= 0f) return;
        if (dt > 0.05f) dt = 0.05f; // clamp a stalled frame so a big jump doesn't teleport

        String dir = flickableDirection.peek();
        boolean allowX = !"VerticalFlick".equals(dir);
        boolean allowY = !"HorizontalFlick".equals(dir);
        float maxX = Math.max(0f, contentWidth.peekFloat() + rightMargin.peekFloat() - width.peekFloat());
        float maxY = Math.max(0f, contentHeight.peekFloat() + bottomMargin.peekFloat() - height.peekFloat());

        if (allowX && flingVX != 0f) {
            float nx = clamp(contentX.peekFloat() + flingVX * dt, 0f, maxX);
            contentX.set(nx);
            if (nx <= 0f || nx >= maxX) flingVX = 0f;
            else flingVX = decay(flingVX, dt);
        }
        if (allowY && flingVY != 0f) {
            float ny = clamp(contentY.peekFloat() + flingVY * dt, 0f, maxY);
            contentY.set(ny);
            if (ny <= 0f || ny >= maxY) flingVY = 0f;
            else flingVY = decay(flingVY, dt);
        }

        if (Math.abs(flingVX) < MIN_FLING && Math.abs(flingVY) < MIN_FLING) {
            flinging = false;
            moving.set(Boolean.FALSE);
        }
    }

    private static float decay(float v, float dt) {
        float d = DECEL * dt;
        if (v > 0f) return Math.max(0f, v - d);
        return Math.min(0f, v + d);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
