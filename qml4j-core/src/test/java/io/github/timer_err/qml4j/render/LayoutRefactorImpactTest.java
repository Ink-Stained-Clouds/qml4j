package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A/B: the SAME scene + the SAME animation, laid out via the incremental polish-queue path
// (this refactor) vs the whole-tree fallback (settleLayoutFull == the pre-refactor behaviour).
// Answers "did the layout refactor actually help" with per-frame node counts and wall time.
class LayoutRefactorImpactTest {

    // A deep tree whose derived heights chain up (nested Columns), like the ClickGUI panels.
    private static String scene(int rows) {
        StringBuilder q = new StringBuilder("import QtQuick\nColumn { id: outer; width: 380\n");
        for (int i = 0; i < rows; i++) {
            q.append("  Column { width: 380\n")
             .append("    Rectangle { width: 380; height: 14 }\n")
             .append("    Rectangle { width: 380; height: 14 }\n")
             .append("    Rectangle { id: r").append(i).append("; width: 380; height: 14 }\n")
             .append("  }\n");
        }
        q.append("}");
        return q.toString();
    }

    private static int countNodes(Item n) {
        int c = 1;
        for (int i = 0; i < n.children.size(); i++) c += countNodes(n.children.get(i));
        return c;
    }

    // Drive an animation on ONE deep rectangle for `frames` frames; return [avgMeasuredNodes,
    // avgLayoutNanos]. incremental=false forces the pre-refactor whole-tree measure each frame.
    private static long[] runAnimation(QmlView v, Item target, boolean incremental, int frames) {
        v.renderer().setIncrementalLayout(incremental);
        v.renderer().requestFullLayout();
        v.pumpLayout(); // settle once under the chosen mode

        long nodeSum = 0;
        long nanoSum = 0;
        for (int f = 0; f < frames; f++) {
            // A size change on the deepest item -- the animation tick.
            target.height.set(14.0 + (f % 8));
            long t0 = System.nanoTime();
            v.pumpLayout();
            nanoSum += System.nanoTime() - t0;
            nodeSum += v.renderer().measuredNodeCount();
        }
        return new long[]{nodeSum / frames, nanoSum / frames};
    }

    @Test
    void incrementalVsWholeTree() {
        int rows = 150;
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item outer = v.load(scene(rows));
        v.pumpLayout();
        int total = countNodes(outer);

        // The deepest rectangle of the first row (outer.children[0] is the first inner Column).
        Item target = outer.children.get(0).children.get(2);

        int frames = 200;
        // Warm up the JIT for both paths before timing.
        runAnimation(v, target, false, 50);
        runAnimation(v, target, true, 50);

        long[] whole = runAnimation(v, target, false, frames);
        long[] incr = runAnimation(v, target, true, frames);

        System.out.printf(
            "[layout-impact] tree=%d nodes | whole-tree: %d nodes/frame, %.1f us/frame | "
            + "incremental: %d nodes/frame, %.1f us/frame | speedup x%.1f%n",
            total, whole[0], whole[1] / 1e3, incr[0], incr[1] / 1e3,
            whole[1] / (double) Math.max(1, incr[1]));

        // Correctness: both paths converge the derived heights identically (outer height is the
        // same whether measured whole-tree or incrementally).
        double outerH = outer.height.peek().doubleValue();
        v.renderer().setIncrementalLayout(false);
        v.renderer().requestFullLayout();
        v.pumpLayout();
        assertEquals(outerH, outer.height.peek().doubleValue(), 1e-6,
            "incremental and whole-tree agree on derived height");

        // The refactor's whole point: incremental touches far fewer nodes per animation frame.
        assertTrue(incr[0] * 10 < whole[0],
            "incremental measures <<1/10 the nodes: incr=" + incr[0] + " whole=" + whole[0]);
    }
}
