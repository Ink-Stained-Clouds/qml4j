package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Probe: real MD3 Slider (track segments + draggable handle + value label,
// readonly alias to MouseArea state, mapFromItem hit math) load.
class SliderLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = SliderLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Item load(String body) {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Slider.qml"}) {
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        }
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Item { width: 200; height: 44\n" +
            "  " + body + "\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        return root;
    }

    @Test
    void loadsSlider() {
        assertFalse(load("Slider { from: 0; to: 100; value: 40 }").children.isEmpty());
    }

    @Test
    void loadsRangeSlider() {
        assertFalse(load("Slider { rangeMode: true; from: 0; to: 1; firstValue: 0.2; secondValue: 0.8 }")
            .children.isEmpty());
    }
}
