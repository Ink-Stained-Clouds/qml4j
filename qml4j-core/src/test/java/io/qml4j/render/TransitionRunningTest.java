package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A Transition drives its `running` property while its animations play, so QML's
// `onRunningChanged` fires on completion -- the hook MD3 Menu uses to tear down its
// overlay after the close animation (without it the scrim stays up and eats input).
class TransitionRunningTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static long readInt(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).longValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void onRunningChangedFiresWhenTransitionCompletes() {
        QmlView v = newView();
        Item box = v.load(
            "Rectangle {\n"
            + "  id: box\n"
            + "  property int doneCount: 0\n"
            + "  width: 100; height: 100\n"
            + "  states: [ State { name: \"big\"; PropertyChanges { target: box; width: 200 } } ]\n"
            + "  transitions: [ Transition {\n"
            + "    NumberAnimation { properties: \"width\"; duration: 100 }\n"
            + "    onRunningChanged: { if (!running) box.doneCount = box.doneCount + 1 }\n"
            + "  } ]\n"
            + "}");

        box.state.set("big");
        long t = 1_000_000_000L;
        for (int f = 0; f < 12; f++) {
            t += 16_000_000L;
            DirtyQueue dq = v.dirtyQueue();
            dq.install();
            try { v.tickAnimations(t); dq.flush(); } finally { dq.uninstall(); }
        }

        assertEquals(200.0, box.width.peek().doubleValue(), 1e-6, "width animated to target");
        assertEquals(1L, readInt(box, "doneCount"), "onRunningChanged fired once on completion");
    }
}
