package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RippleFadeTest {
    private static byte[] res(String p) {
        try (InputStream in = RippleFadeTest.class.getResourceAsStream("/" + p)) {
            assertNotNull(in, "missing " + p); return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private long clock = 1_000_000_000L;
    private void tick(QmlView v, long ms) {
        clock += ms * 1_000_000L;
        DirtyQueue dq = v.dirtyQueue(); dq.install();
        try { v.tickAnimations(clock); dq.flush(); } finally { dq.uninstall(); }
    }
    private Item rippleOf(QmlView v) {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir","Theme.qml","Ripple.qml"})
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 80; height: 80\n  Ripple { id: r; width: 80; height: 80; clipRadius: 20 }\n}");
        return root.children.get(0).children.get(1).children.get(0); // root>Ripple>rippleContent>ripple
    }

    @Test
    void rippleFadesOutAfterRelease() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item ripple = rippleOf(v);
        tick(v, 1);
        v.dispatchPointerDown(40, 40);
        tick(v, 1);   // establish fade-in start time
        tick(v, 250); // fade-in (200ms) completes -> at peak
        double pressed = ripple.opacity.peek().doubleValue();
        v.dispatchPointerUp(40, 40);
        tick(v, 1);   // hold-to-peak leg starts (frac 0)
        tick(v, 95);  // hold leg (90ms, no-op at peak) done -> fade leg starts
        tick(v, 120); // 120ms into the 300ms fade
        double mid = ripple.opacity.peek().doubleValue();
        tick(v, 350);
        double after = ripple.opacity.peek().doubleValue();
        System.out.println("FULLPRESS pressed=" + pressed + " mid=" + mid + " after=" + after);
        assertTrue(pressed > 0.10, "ripple at peak during press, was " + pressed);
        assertTrue(mid > 0.0 && mid < pressed, "ripple fading, was " + mid);
        assertEquals(0.0, after, 1e-6, "ripple gone after fade");
    }

    @Test
    void retapWhileFadingDoesNotBlinkOut() {
        // Tapping again before the previous ripple has faded must not snap opacity
        // to 0 (old wave blinking out); the new press resumes from current opacity.
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item ripple = rippleOf(v);
        tick(v, 1);
        v.dispatchPointerDown(40, 40);
        tick(v, 1);
        tick(v, 250);                // first ripple at peak
        v.dispatchPointerUp(40, 40);
        tick(v, 1);
        tick(v, 95);                 // hold-to-peak done, fade leg running
        tick(v, 150);                // mid fade-out
        double fading = ripple.opacity.peek().doubleValue();
        assertTrue(fading > 0.02 && fading < 0.12, "first ripple mid-fade, was " + fading);
        v.dispatchPointerDown(20, 20); // re-tap elsewhere while fading
        tick(v, 1);
        double afterRetap = ripple.opacity.peek().doubleValue();
        System.out.println("RETAP fading=" + fading + " afterRetap=" + afterRetap);
        assertTrue(afterRetap >= fading - 1e-6,
            "re-tap keeps (or raises) opacity, did not blink to 0; was " + afterRetap);
    }

    @Test
    void quickTapStillReachesPeak() {
        // MD3 "minimum visible ripple": a fast tap (release before fade-in ramps)
        // must still rise to full opacity before fading, not stay faint.
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item ripple = rippleOf(v);
        tick(v, 1);
        v.dispatchPointerDown(40, 40);
        tick(v, 1);   // fade-in barely started: opacity ~ 0
        double atPress = ripple.opacity.peek().doubleValue();
        v.dispatchPointerUp(40, 40); // release almost immediately
        tick(v, 1);
        tick(v, 95);  // hold-to-peak leg (~90ms) completes
        double peak = ripple.opacity.peek().doubleValue();
        tick(v, 350); // fade-out completes
        double after = ripple.opacity.peek().doubleValue();
        System.out.println("QUICKTAP atPress=" + atPress + " peak=" + peak + " after=" + after);
        assertTrue(atPress < 0.02, "fade-in had barely begun, was " + atPress);
        assertTrue(peak > 0.10, "quick tap still reaches near-full opacity, was " + peak);
        assertEquals(0.0, after, 1e-6, "ripple gone after fade");
    }
}
