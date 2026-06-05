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
    void tapSpawnsAVisibleWaveThatFadesAndDestroysItself() {
        QmlView v = load();
        tick(v, 1);
        assertEquals(0, content.children.size(), "no waves before any tap");
        v.dispatchPointerDown(40, 40);
        tick(v, 1);
        assertEquals(1, content.children.size(), "one wave spawned on press");
        tick(v, 120);
        double peak = opacityOf(content.children.get(0));
        assertTrue(peak > 0.10, "wave rises to near-full opacity, was " + peak);
        v.dispatchPointerUp(40, 40);
        tick(v, 600); // past the 470ms self-destruct timer
        assertEquals(0, content.children.size(), "wave destroyed itself after fading");
    }

    @Test
    void concurrentTapsCoexistAsSeparateWaves() {
        QmlView v = load();
        tick(v, 1);
        v.dispatchPointerDown(20, 20);  // first wave
        tick(v, 1);
        tick(v, 150);                   // let the first wave ramp + expand
        v.dispatchPointerUp(20, 20);
        v.dispatchPointerDown(60, 60);  // second wave, while first still alive
        tick(v, 1);
        assertEquals(2, content.children.size(), "both waves coexist");
        Item w1 = content.children.get(0), w2 = content.children.get(1);
        tick(v, 30);
        // First wave is older/bigger; both are independently visible.
        assertTrue(opacityOf(w1) > 0.0, "first wave still visible, was " + opacityOf(w1));
        assertTrue(opacityOf(w2) > 0.0, "second wave visible, was " + opacityOf(w2));
        assertTrue(w1.width.peek().doubleValue() > w2.width.peek().doubleValue(),
            "older wave has expanded further");
        v.dispatchPointerUp(60, 60);
        tick(v, 800);
        assertEquals(0, content.children.size(), "both waves eventually destroyed");
    }
}
