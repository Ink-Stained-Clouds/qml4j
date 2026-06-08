package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.view.Loader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// End-to-end of the MD3 app's page switch: Loader.onLoaded starts a standalone
// ParallelAnimation that slides the new page up (y 50 -> 0) and fades it in
// (opacity 0 -> 1). Verifies the page floats in instead of snapping.
class PageEnterAnimationTest {

    private static final String SRC =
        "import QtQuick\n"
        + "Item {\n"
        + "  id: page\n"
        + "  width: 200; height: 200\n"
        + "  function playEnter(t) {\n"
        + "    enterAnim.stop(); ay.target = t; ao.target = t;\n"
        + "    t.opacity = 0; t.y = 50; enterAnim.start();\n"
        + "  }\n"
        + "  Loader {\n"
        + "    id: ld\n"
        + "    sourceComponent: Component { Rectangle { width: 10; height: 10 } }\n"
        + "    onLoaded: page.playEnter(ld.item)\n"
        + "  }\n"
        + "  ParallelAnimation {\n"
        + "    id: enterAnim\n"
        + "    NumberAnimation { id: ao; property: \"opacity\"; to: 1; duration: 300 }\n"
        + "    NumberAnimation { id: ay; property: \"y\"; to: 0; duration: 300 }\n"
        + "  }\n"
        + "}";

    private long clock = 4_000_000_000L;

    private void tick(QmlView v, long deltaMs) {
        clock += deltaMs * 1_000_000L;
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { v.tickAnimations(clock); dq.flush(); } finally { dq.uninstall(); }
    }

    @Test
    void pageSlidesUpAndFadesIn() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item page = v.load(SRC);
        page.width.set(200.0);
        page.height.set(200.0);

        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { new Renderer().layoutOnly(page); } finally { dq.uninstall(); }

        Loader ld = (Loader) page.children.get(0);
        Item loaded = ld.loadedItem;
        // onLoaded ran playEnter: the page starts offset and transparent.
        assertEquals(50.0, loaded.y.peek().doubleValue(), 1e-6, "starts 50px below");
        assertEquals(0.0, loaded.opacity.peek().doubleValue(), 1e-6, "starts transparent");

        tick(v, 1);
        tick(v, 150);
        double midY = loaded.y.peek().doubleValue();
        double midO = loaded.opacity.peek().doubleValue();
        assertTrue(midY > 0.0 && midY < 50.0, "mid-slide, was y=" + midY);
        assertTrue(midO > 0.0 && midO < 1.0, "mid-fade, was opacity=" + midO);

        tick(v, 300);
        assertEquals(0.0, loaded.y.peek().doubleValue(), 1e-6, "settles at y=0");
        assertEquals(1.0, loaded.opacity.peek().doubleValue(), 1e-6, "settles opaque");
    }
}
