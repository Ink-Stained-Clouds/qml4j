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
    @Test
    void rippleFadesOutAfterRelease() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir","Theme.qml","Ripple.qml"})
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 80; height: 80\n  Ripple { id: r; width: 80; height: 80; clipRadius: 20 }\n}");
        Item ripple = root.children.get(0).children.get(1).children.get(0); // root>Ripple>rippleContent>ripple
        tick(v, 1);
        v.dispatchPointerDown(40, 40);
        tick(v, 1);   // establish fade-in start time (frac 0)
        tick(v, 150); // ramp fade-in (200ms anim)
        double pressed = ripple.opacity.peek().doubleValue();
        v.dispatchPointerUp(40, 40);
        tick(v, 1);   // establish fade-out start time
        tick(v, 150); // 150ms into 300ms fade-out
        double mid = ripple.opacity.peek().doubleValue();
        tick(v, 300); // well past fade-out
        double after = ripple.opacity.peek().doubleValue();
        System.out.println("RIPPLE pressed=" + pressed + " mid=" + mid + " after=" + after);
        assertTrue(pressed > 0.0, "ripple visible during press, was " + pressed);
        assertTrue(mid > 0.0 && mid < pressed, "ripple still fading 100ms after release, was " + mid);
        assertEquals(0.0, after, 1e-6, "ripple gone after fade");
    }
}
