package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
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
    private void frames(QmlView v, int n) { for (int i = 0; i < n; i++) tick(v, 16); }

    private Item content; // rippleContent: parent of live waves
    private QmlView load() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir","Theme.qml","Ripple.qml"})
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 80; height: 80\n  Ripple { id: r; width: 80; height: 80; clipRadius: 20 }\n}");
        content = root.children.get(0).children.get(1); // root>Ripple>rippleContent
        return v;
    }
    private static double opacityOf(Item wave) { return wave.opacity.peek().doubleValue(); }

    @Test
    void waveHoldsWhilePressedAndFadesOnlyOnRelease() {
        QmlView v = load();
        tick(v, 1);
        v.dispatchPointerDown(40, 40);
        frames(v, 8); // ~128ms: expand + fade-in to full
        assertEquals(1, content.children.size(), "wave spawned on press");
        Item wave = content.children.get(0);
        assertTrue(opacityOf(wave) > 0.10, "wave at full opacity while pressed");
        // Hold a long time -- must NOT fade or destroy itself.
        frames(v, 80); // ~1.3s held
        assertEquals(1, content.children.size(), "wave persists while held");
        assertTrue(opacityOf(wave) > 0.119, "wave still at full opacity while held, was " + opacityOf(wave));
        // Release -> fades out, then destroys itself.
        v.dispatchPointerUp(40, 40);
        frames(v, 18); // into the fade (after the brief hold-to-full leg)
        assertTrue(opacityOf(wave) < 0.119 && opacityOf(wave) > 0.0,
            "wave fading after release, was " + opacityOf(wave));
        frames(v, 25); // past the ~380ms self-destruct
        assertEquals(0, content.children.size(), "wave destroyed after fade-out");
    }

    @Test
    void concurrentTapsCoexistAsSeparateWaves() {
        QmlView v = load();
        tick(v, 1);
        v.dispatchPointerDown(20, 20);  // first wave
        frames(v, 10);                  // let the first wave ramp + expand
        v.dispatchPointerUp(20, 20);    // first wave begins fading
        v.dispatchPointerDown(60, 60);  // second wave, while first still alive
        frames(v, 2);
        assertEquals(2, content.children.size(), "both waves coexist");
        Item w1 = content.children.get(0), w2 = content.children.get(1);
        assertTrue(opacityOf(w1) > 0.0, "first (fading) wave still visible, was " + opacityOf(w1));
        assertTrue(opacityOf(w2) > 0.0, "second (held) wave visible, was " + opacityOf(w2));
        assertTrue(w1.width.peek().doubleValue() > w2.width.peek().doubleValue(),
            "older wave has expanded further");
        v.dispatchPointerUp(60, 60);
        frames(v, 40);
        assertEquals(0, content.children.size(), "both waves eventually destroyed");
    }
}
