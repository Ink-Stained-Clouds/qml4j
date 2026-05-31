package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateToggleAnimTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    // Mirrors demo.qml's "tap to toggle" box: a Transition (not Behavior)
    // animates width/height between the base state and the "big" state.
    private static final String SRC =
        "Rectangle {\n" +
        "  id: box\n" +
        "  x: 60; width: 280; height: 280\n" +
        "  states: [ State { name: \"big\"; PropertyChanges { target: box; width: 720; height: 720 } } ]\n" +
        "  transitions: [ Transition { NumberAnimation { properties: \"width,height\"; duration: 100 } } ]\n" +
        "}";

    // Monotonic clock shared across the whole test (real frame time never
    // goes backwards; using decreasing stamps produced negative tween frac).
    private long clock = 1_000_000_000L;

    private void tick(QmlView v, long deltaMs) {
        clock += deltaMs * 1_000_000L;
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            v.tickAnimations(clock);
            dq.flush();
        } finally {
            dq.uninstall();
        }
    }

    // Run a tween (100ms) to completion: start tick + a tick past the end.
    private void settle(QmlView v) {
        tick(v, 1);
        tick(v, 200);
    }

    private static double w(Item box) {
        return box.width.peek().doubleValue();
    }

    @Test
    void toggleFourTimesEndsAtCorrectValues() {
        QmlView v = newView();
        Item box = v.load(SRC);
        assertEquals(280.0, w(box), 1e-6);

        box.state.set("big"); settle(v);
        assertEquals(720.0, w(box), 1e-6, "1st toggle -> big");
        box.state.set("");    settle(v);
        assertEquals(280.0, w(box), 1e-6, "2nd toggle -> base");
        box.state.set("big"); settle(v);
        assertEquals(720.0, w(box), 1e-6, "3rd toggle -> big again");
        box.state.set("");    settle(v);
        assertEquals(280.0, w(box), 1e-6, "4th toggle -> base again");
    }

    @Test
    void everyToggleActuallyAnimates() {
        QmlView v = newView();
        Item box = v.load(SRC);
        String[] targets = {"big", "", "big", ""};
        for (int i = 0; i < targets.length; i++) {
            box.state.set(targets[i]);
            tick(v, 1);    // start the tween's clock
            tick(v, 50);   // 50ms into a 100ms tween -> mid-value
            double mid = w(box);
            assertTrue(mid > 280.0 && mid < 720.0,
                "toggle " + (i + 1) + " (-> " + (targets[i].isEmpty() ? "base" : "big")
                + ") should be mid-tween, was " + mid);
            settle(v);     // finish it before the next toggle
        }
    }

    // Exactly demo.qml's box: PropertyChanges sets FIVE props (width, height,
    // color, x, y) but the Transition only animates width,height. Mixing
    // animated + instant props in one PropertyChanges is the untested combo.
    private static final String SRC5 =
        "Rectangle {\n" +
        "  id: box\n" +
        "  x: 60; y: 120; width: 280; height: 280; color: \"#ff5050\"\n" +
        "  states: [ State { name: \"big\"; PropertyChanges { target: box;\n" +
        "    width: 720; height: 720; color: \"#5050ff\"; x: 60; y: 120 } } ]\n" +
        "  transitions: [ Transition { NumberAnimation {\n" +
        "    properties: \"width,height\"; duration: 100; easing: \"easeOutQuad\" } } ]\n" +
        "}";

    @Test
    void demoExactFiveProps() {
        QmlView v = newView();
        Item box = v.load(SRC5);
        DirtyQueue dq = v.dirtyQueue();
        long t = 7_000_000_000L;
        String[] targets = {"big", "", "big", "", "big", ""};
        double[] expected = {720, 280, 720, 280, 720, 280};
        for (int i = 0; i < targets.length; i++) {
            box.state.set(targets[i]);
            for (int f = 0; f < 12; f++) {
                t += 16_000_000L;
                dq.install();
                try { v.tickAnimations(t); dq.flush(); }
                finally { dq.uninstall(); }
            }
            assertEquals(expected[i], w(box), 1e-6,
                "5-prop toggle " + (i + 1) + " should settle at " + expected[i]
                + ", was " + w(box));
        }
    }

    // Closest reproduction of the device: a continuous 16ms frame loop that
    // keeps ticking even when idle, with toggles fired between frames and a
    // toggle that lands WHILE the previous tween is still running.
    @Test
    void continuousFrameLoopWithMidAnimationToggles() {
        QmlView v = newView();
        Item box = v.load(SRC);
        DirtyQueue dq = v.dirtyQueue();
        long t = 5_000_000_000L;
        String[] targets = {"big", "", "big", "", "big"};
        double[] expected = {720, 280, 720, 280, 720};
        for (int i = 0; i < targets.length; i++) {
            box.state.set(targets[i]);
            // run ~12 frames of 16ms = ~192ms, well past the 100ms tween
            for (int f = 0; f < 12; f++) {
                t += 16_000_000L;
                dq.install();
                try { v.tickAnimations(t); dq.flush(); }
                finally { dq.uninstall(); }
            }
            assertEquals(expected[i], w(box), 1e-6,
                "frame-loop toggle " + (i + 1) + " should settle at " + expected[i]
                + ", was " + w(box));
        }
    }
}
