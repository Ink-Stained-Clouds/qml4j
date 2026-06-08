package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A nested SequentialAnimation > ParallelAnimation { NumberAnimation; SequentialAnimation }
// driving a bar's x/width, gated on a `running:` binding that flips true after construction
// (the MD3 indeterminate LinearProgress, whose `indeterminate` is bound to a switch).
class IndeterminateProgressAnimTest {

    @Test
    void runningBindingFlipStartsNestedAnimation() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Item { id: control; width: 200; height: 4\n" +
            "  property bool indeterminate: false\n" +
            "  Item { id: holder; width: 200; height: 4\n" +
            "  Rectangle { id: bar; height: 4\n" +
            "    SequentialAnimation {\n" +
            "      running: control.indeterminate\n" +
            "      loops: Animation.Infinite\n" +
            "      ParallelAnimation {\n" +
            "        NumberAnimation { target: bar; property: \"x\"; from: -parent.width; to: parent.width; duration: 2000 }\n" +
            "        SequentialAnimation {\n" +
            "          NumberAnimation { target: bar; property: \"width\"; from: 0; to: parent.width * 0.5; duration: 1000 }\n" +
            "          NumberAnimation { target: bar; property: \"width\"; from: parent.width * 0.5; to: 0; duration: 1000 }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "  }\n" +
            "}");
        Item bar = (Item) field(root, "bar");
        @SuppressWarnings("unchecked")
        io.github.timer_err.qml4j.engine.binding.Property<Boolean> indeterminate =
            (io.github.timer_err.qml4j.engine.binding.Property<Boolean>) field(root, "indeterminate");

        long t = 0L;
        v.tickAnimations(t);                 // running is false -> nothing
        double xBefore = bar.x.peekDouble();

        indeterminate.set(Boolean.TRUE);     // the switch flips on
        v.tickAnimations(t);                 // first tick: onStart
        v.tickAnimations(t += 500_000_000L); // +0.5s
        double xMid = bar.x.peekDouble();
        double wMid = bar.width.peekDouble();

        assertNotEquals(xBefore, xMid, "bar.x advanced once the running binding flipped true");
        assertTrue(wMid > 0, "the nested width sub-animation also drives width");
    }

    private static Object field(Object root, String name) {
        try {
            return root.getClass().getField(name).get(root);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
