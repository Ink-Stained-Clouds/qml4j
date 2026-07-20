package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Regression + perf guards for the incremental (polish-queue) layout pipeline. Each case is
// one of the failures the old cachedLayout heuristic produced, plus the O(1)-drag perf target.
class RelayoutInvalidationTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    // Failure A -- derived size must not lag one pass. A deep descendant's height change must
    // converge up every size-deriving ancestor in the SAME settle (no stale intermediate).
    @Test
    void derivedHeightConvergesSameFrame() {
        QmlView v = newView();
        Item c = v.load(
            "import QtQuick\n"
            + "Column {\n"
            + "  Column {\n"
            + "    Rectangle { width: 10; height: 20 }\n"
            + "  }\n"
            + "}");
        v.pumpLayout(); // first full layout
        Item r = c.children.get(0);
        Item d = r.children.get(0);
        assertEquals(20.0, r.height.peek().doubleValue(), 1e-6);
        assertEquals(20.0, c.height.peek().doubleValue(), 1e-6);

        // Simulate an animation tick growing the deepest item.
        d.height.set(50.0);
        v.pumpLayout();

        assertEquals(50.0, r.height.peek().doubleValue(), 1e-6, "inner Column height follows child same frame");
        assertEquals(50.0, c.height.peek().doubleValue(), 1e-6, "outer Column height follows inner same frame");
    }

    // Failure B -- an in-place size animation that re-centres via anchors.verticalCenter must
    // re-resolve its anchor every change (never skipped as a static subtree).
    @Test
    void inPlaceResizeReanchorsCenter() {
        QmlView v = newView();
        Item track = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  width: 52; height: 32\n"
            + "  Rectangle {\n"
            + "    width: 16; height: 16\n"
            + "    anchors.verticalCenter: parent.verticalCenter\n"
            + "  }\n"
            + "}");
        v.pumpLayout();
        Item knob = track.children.get(0);
        assertEquals(8.0, knob.y.peek().doubleValue(), 1e-6, "centred at (32-16)/2");

        // Grow the knob in place (the switch-thumb animation).
        knob.height.set(24.0);
        v.pumpLayout();

        assertEquals(4.0, knob.y.peek().doubleValue(), 1e-6, "re-centred at (32-24)/2, not stuck at 8");
    }

    // Perf -- dragging one absolutely positioned child (x/y only) must touch ~O(1) nodes, not
    // the whole tree. Asserts the measured-node count stays tiny regardless of sibling count.
    @Test
    void absolutePositionDragIsLocal() {
        StringBuilder qml = new StringBuilder(
            "import QtQuick\n" + "Item {\n  width: 400; height: 400\n");
        int n = 40;
        for (int i = 0; i < n; i++) {
            qml.append("  Rectangle { x: ").append(i).append("; y: 0; width: 10; height: 10 }\n");
        }
        qml.append("}");

        QmlView v = newView();
        Item root = v.load(qml.toString());
        v.pumpLayout(); // full layout of all n children
        assertTrue(v.renderer().measuredNodeCount() >= n, "full layout measures the whole tree");

        // Drag one child: change only its x.
        Item child = root.children.get(0);
        child.x.set(123.0);
        v.pumpLayout();

        int measured = v.renderer().measuredNodeCount();
        assertTrue(measured <= 2, "moving one absolute child measures ~O(1) nodes, got " + measured);
        assertEquals(123.0, child.x.peek().doubleValue(), 1e-6);
    }
}
