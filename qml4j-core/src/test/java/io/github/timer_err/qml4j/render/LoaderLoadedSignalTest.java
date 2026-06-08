package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Qt's Loader emits onLoaded once its item is instantiated. The MD3 app's page
// switcher hangs its enter animation (slide-up + fade-in) off pageLoader.onLoaded;
// without the signal the page just snaps in.
class LoaderLoadedSignalTest {

    @Test
    void loaderEmitsOnLoadedWhenComponentInstantiated() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  id: page\n"
            + "  property int loads: 0\n"
            + "  property real lastY: -1\n"
            + "  Loader {\n"
            + "    id: ld\n"
            + "    sourceComponent: Component { Rectangle { width: 10; height: 10 } }\n"
            + "    onLoaded: { page.loads = page.loads + 1; page.lastY = ld.item.y }\n"
            + "  }\n"
            + "}");
        root.width.set(100.0);
        root.height.set(100.0);

        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { new Renderer().layoutOnly(root); } finally { dq.uninstall(); }

        assertEquals(1L, asLong(root, "loads"), "Loader.onLoaded fired once after instantiation");
    }

    private static long asLong(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).longValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
