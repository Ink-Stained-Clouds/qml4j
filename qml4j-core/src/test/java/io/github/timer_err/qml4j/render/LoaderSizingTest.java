package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.view.Loader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// A Loader's implicit size follows its loaded content (Qt behavior), so a Loader used
// as a layout/Repeater delegate -- e.g. MD3 Menu items -- has height instead of
// collapsing to 0.
class LoaderSizingTest {

    @Test
    void loaderSizesToLoadedContent() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  Loader { id: ld; sourceComponent: Component {\n"
            + "    Rectangle { implicitWidth: 50; implicitHeight: 30 }\n"
            + "  } }\n"
            + "}");
        root.width.set(200.0);
        root.height.set(200.0);
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { new Renderer().layoutOnly(root); } finally { dq.uninstall(); }

        Loader ld = (Loader) root.children.get(0);
        assertNotNull(ld.loadedItem, "component loaded");
        assertEquals(30.0, ld.implicitHeight.peek().doubleValue(), 1e-6, "loader implicit height = content");
        assertEquals(50.0, ld.implicitWidth.peek().doubleValue(), 1e-6, "loader implicit width = content");
    }
}
