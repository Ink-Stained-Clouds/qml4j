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
    @SuppressWarnings("unused")
    public final Property<Number> boundsBehavior = new Property<>(3); // DragAndOvershootBounds
    // Content margins (Qt Flickable.topMargin/...): accepted so documents load; the
    // content is laid out by its own anchors/size here, so these are not yet applied.
    @SuppressWarnings("unused")
    public final Property<Number> topMargin = new Property<>(0);
    public final Property<Number> bottomMargin = new Property<>(0);
    @SuppressWarnings("unused")
    public final Property<Number> leftMargin = new Property<>(0);
    public final Property<Number> rightMargin = new Property<>(0);
    // Touch press-delay before the flick steals the press from children; accepted so
    // documents load (no delayed-press synthesis here).
    @SuppressWarnings("unused")
    public final Property<Number> pressDelay = new Property<>(0);

    // --- Eased scrolling + fling -----------------------------------------
    // Drag/wheel/fling set a target position; the shown contentX/Y eases toward
    // it each frame (a smoothing layer over the raw input). On release the
    // residual velocity keeps advancing the target (inertia). Stepped by tick()
    // since Flickable is Animatable and the render loop walks those.
    private float targetX;
    private float targetY;
    private boolean smoothing;
    private float flingVX;
    private float flingVY;
    private boolean flinging;
    private long lastNanos;
    private static final float DECEL = 2200f;    // px/s^2
    private static final float MIN_FLING = 50f;  // px/s
    private static final float EASE = 18f;       // higher = snappier follow

    public Flickable() {
        // When the content shrinks (a collapsed section) or the viewport resizes, the
        // shown/target scroll can sit past the new bottom; re-clamp so the content
        // doesn't get stuck scrolled off the top with no way back.
        contentHeight.addListener(v -> clampToBounds());
        contentWidth.addListener(v -> clampToBounds());
        height.addListener(v -> clampToBounds());
        width.addListener(v -> clampToBounds());
        // Scrolling translates the content at draw time -- a pure paint change (setPaintOnly,
        // no version bump), but it does alter the recorded pixels, so invalidate the cache.
        wireContentInvalidation(contentX, contentY);
    }

    private void clampToBounds() {
        float mx = maxX(), my = maxY();
        float cx = contentX.peekFloat();
        float cy = contentY.peekFloat();
        float ncx = clamp(cx, 0f, mx);
        float ncy = clamp(cy, 0f, my);
        if (ncx != cx) contentX.setPaintOnly(ncx);
        if (ncy != cy) contentY.setPaintOnly(ncy);
        targetX = clamp(targetX, 0f, mx);
        targetY = clamp(targetY, 0f, my);
    }

    private float maxX() {
        return Math.max(0f, contentWidth.peekFloat() + rightMargin.peekFloat() - width.peekFloat());
    }

    private float maxY() {
        return Math.max(0f, contentHeight.peekFloat() + bottomMargin.peekFloat() - height.peekFloat());
    }

    /** Set the absolute scroll target (clamped); content eases toward it. */
    public void setScrollTarget(float x, float y) {
        targetX = clamp(x, 0f, maxX());
        targetY = clamp(y, 0f, maxY());
        smoothing = true;
    }

    /** Adjust the target by a delta (wheel notches accumulate before the ease catches up). */
    @SuppressWarnings("unused")
    public void nudge(float dx, float dy) {
        setScrollTarget(targetX + dx, targetY + dy);
    }

    /** Pin the target to the current (instantly-set) content position. Drag and
     *  wheel set contentX/Y directly for a 1:1 feel; this keeps the eased target
     *  in step so a following fling coasts from the right place. */
    public void syncTarget() {
        targetX = contentX.peekFloat();
        targetY = contentY.peekFloat();
    }

    /** A fresh touch: drop any coast and pin the target to where we are now. */
    public void stopScroll() {
        flinging = false;
        smoothing = false;
        syncTarget();
    }

    @SuppressWarnings("unused")
    public void stopFling() {
        flinging = false;
    }

    /** Release velocity (content px/sec) that keeps advancing the target; the
     *  shown position eases after it (the smooth inertial glide). */
    public void startFling(float vx, float vy) {
        flingVX = vx;
        flingVY = vy;
        flinging = Math.abs(vx) > MIN_FLING || Math.abs(vy) > MIN_FLING;
        smoothing = flinging;
        moving.set(flinging);
    }

    @SuppressWarnings("unused")
    public float targetX() {
        return targetX;
    }

    @SuppressWarnings("unused")
    public float targetY() {
        return targetY;
    }

    @Override
    public void tick(long nowNanos) {
        if (!smoothing && !flinging) return;
        if (lastNanos == 0L) {
            lastNanos = nowNanos;
            return;
        }
        float dt = (nowNanos - lastNanos) / 1_000_000_000f;
        lastNanos = nowNanos;
        if (dt <= 0f) return;
        if (dt > 0.05f) dt = 0.05f;

        String dir = flickableDirection.peek();
        boolean allowX = !"VerticalFlick".equals(dir);
        boolean allowY = !"HorizontalFlick".equals(dir);
        float mx = maxX(), my = maxY();

        // Inertia advances the target; hitting an edge kills that axis.
        if (flinging) {
            if (allowX && flingVX != 0f) {
                targetX = clamp(targetX + flingVX * dt, 0f, mx);
                flingVX = (targetX <= 0f || targetX >= mx) ? 0f : decay(flingVX, dt);
            }
            if (allowY && flingVY != 0f) {
                targetY = clamp(targetY + flingVY * dt, 0f, my);
                flingVY = (targetY <= 0f || targetY >= my) ? 0f : decay(flingVY, dt);
            }
            if (Math.abs(flingVX) < MIN_FLING && Math.abs(flingVY) < MIN_FLING) {
                flinging = false;
            }
        }

        // Ease the shown position toward the target (frame-rate independent).
        float a = 1f - (float) Math.exp(-EASE * dt);
        float cx = contentX.peekFloat();
        float cy = contentY.peekFloat();
        float nx = cx + (targetX - cx) * a;
        float ny = cy + (targetY - cy) * a;
        if (Math.abs(targetX - nx) < 0.25f) nx = targetX;
        if (Math.abs(targetY - ny) < 0.25f) ny = targetY;
        // Paint-only: a scroll offset is a draw-time translate, so it must not force
        // a relayout -- dependents still react, but a pure scroll keeps the fast path.
        if (allowX) contentX.setPaintOnly(nx);
        if (allowY) contentY.setPaintOnly(ny);

        if (!flinging && nx == targetX && ny == targetY) {
            smoothing = false;
            moving.set(Boolean.FALSE);
        }
    }

    private static float decay(float v, float dt) {
        float d = DECEL * dt;
        if (v > 0f) return Math.max(0f, v - d);
        return Math.min(0f, v + d);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
