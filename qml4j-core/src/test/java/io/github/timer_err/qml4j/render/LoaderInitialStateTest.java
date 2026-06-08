package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.view.Loader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// The MD3 app reaches every page through a Loader (AppRoot's pageLoader). The
// NavigationBar pill is a child whose width is driven 0 -> 64 by a State whose
// `when` is already true at instantiation. This verifies that a component
// instantiated dynamically by a Loader gets its initial `when`-state applied --
// the same way a top-level-loaded component does.
class LoaderInitialStateTest {

    private static final String SRC =
        "import QtQuick\n"
        + "Item {\n"
        + "  Loader { id: ld; sourceComponent: Component {\n"
        + "    Item {\n"
        + "      property bool sel: true\n"
        + "      Rectangle {\n"
        + "        id: pill\n"
        + "        width: 0; height: 32\n"
        + "        states: State { name: \"selected\"; when: parent.parent.sel\n"
        + "          PropertyChanges { target: pill; width: 64 } }\n"
        + "        transitions: Transition { from: \"\"; to: \"selected\"\n"
        + "          NumberAnimation { property: \"width\"; duration: 150 } }\n"
        + "      }\n"
        + "    }\n"
        + "  } }\n"
        + "}";

    @Test
    void loaderInstantiatedComponentAppliesInitialWhenState() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(SRC);
        root.width.set(200.0);
        root.height.set(200.0);

        DirtyQueue dq = v.dirtyQueue();
        long clock = 3_000_000_000L;
        // A few frames: resolve the Loader, flush bindings, run the entry tween.
        for (int i = 0; i < 20; i++) {
            clock += 16_000_000L;
            dq.install();
            try {
                new Renderer().layoutOnly(root);
                v.tickAnimations(clock);
                dq.flush();
            } finally {
                dq.uninstall();
            }
        }

        Loader ld = (Loader) root.children.get(0);
        assertNotNull(ld.loadedItem, "component loaded");
        Item pill = ld.loadedItem.children.get(0);
        assertEquals(64.0, pill.width.peek().doubleValue(), 1e-6,
            "Loader-instantiated pill should apply its initial when-state (width 64)");
    }
}
