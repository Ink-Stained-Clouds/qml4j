package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The MD3 NavigationRail pill is a Rectangle whose colour is transparent unless the
// item is selected, with a `Behavior on color`. On construction the Behavior must NOT
// animate the initial assignment from the Rectangle's default white -- otherwise every
// unselected pill flashes white and fades to transparent on entry.
class BehaviorColorInitFlashTest {

    private static final String SRC =
        "import QtQuick\n"
        + "Item {\n"
        + "  width: 200; height: 200\n"
        + "  Column {\n"
        + "    Repeater {\n"
        + "      model: 3\n"
        + "      Rectangle {\n"
        + "        width: 56; height: 32\n"
        + "        color: index === 0 ? \"#ff0000\" : \"transparent\"\n"
        + "        Behavior on color { ColorAnimation { duration: 200 } }\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}";

    private long clock = 8_000_000_000L;

    private void frame(QmlView v, Item root, long deltaMs) {
        clock += deltaMs * 1_000_000L;
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            new Renderer().layoutOnly(root);
            v.tickAnimations(clock);
            dq.flush();
        } finally {
            dq.uninstall();
        }
    }

    @Test
    void unselectedPillIsTransparentImmediatelyNotFlashingWhite() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(SRC);
        root.width.set(200.0);
        root.height.set(200.0);
        frame(v, root, 1);

        Item col = root.children.get(0);
        // children[0] is the Repeater node; the 3 delegates follow.
        Rectangle pill1 = (Rectangle) col.children.get(2);
        assertEquals("transparent", pill1.color.peek(),
            "unselected pill stays transparent, no white-to-transparent flash");
    }
}
