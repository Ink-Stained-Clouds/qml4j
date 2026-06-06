package io.qml4j.render;

import io.qml4j.render.items.animation.NumberAnimation;
import io.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// loops: an infinite animation keeps running past its duration; a finite one stops.
class AnimationLoopsTest {

    private static NumberAnimation anim(int loops) {
        Rectangle r = new Rectangle();
        NumberAnimation a = new NumberAnimation();
        a.target.set(r);
        a.property.set("opacity");
        a.from.set(0.0);
        a.to.set(1.0);
        a.duration.set(100);
        a.loops.set(loops);
        return a;
    }

    @Test
    void infiniteLoopKeepsRunning() {
        NumberAnimation a = anim(-1); // Animation.Infinite
        a.start();
        a.tick(0L);
        a.tick(250_000_000L); // 250ms -> well past 2 durations
        assertTrue(Boolean.TRUE.equals(a.running.peek()), "infinite animation still running");
    }

    @Test
    void singleLoopStops() {
        NumberAnimation a = anim(1);
        a.start();
        a.tick(0L);
        a.tick(150_000_000L); // past the one 100ms run
        assertFalse(Boolean.TRUE.equals(a.running.peek()), "single-shot animation stopped");
    }
}
