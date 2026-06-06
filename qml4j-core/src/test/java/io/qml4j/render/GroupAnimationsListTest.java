package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A group animation exposes its child animations as `animations`, so a running move can
// be retargeted via `anim.animations[0].to = x` -- MD3 Tabs does this to redirect the
// sliding indicator mid-flight.
class GroupAnimationsListTest {

    private static QmlView newView() { return QmlView.withStockTypes(new QmlEngine()); }

    private static double w(Item box) { return box.width.peek().doubleValue(); }

    private static Object call(Item root, String fn) {
        try {
            for (java.lang.reflect.Method m : root.getClass().getMethods()) {
                if (m.getName().equals(fn) && m.getParameterCount() == 0) return m.invoke(root);
            }
            throw new NoSuchMethodException(fn);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void retargetThroughAnimationsListThenRun() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n"
            + "Rectangle { id: box; width: 10; height: 10\n"
            + "  ParallelAnimation { id: anim\n"
            + "    NumberAnimation { target: box; property: \"width\"; duration: 100 }\n"
            + "  }\n"
            + "  function go() { anim.animations[0].to = 200; anim.start() }\n"
            + "}");
        call(root, "go");
        long t = 1_000_000_000L;
        for (int f = 0; f < 12; f++) {
            t += 16_000_000L;
            DirtyQueue dq = v.dirtyQueue();
            dq.install();
            try { v.tickAnimations(t); dq.flush(); } finally { dq.uninstall(); }
        }
        assertEquals(200.0, w(root), 1e-6, "width animated to the to-value set via animations[0]");
    }
}
