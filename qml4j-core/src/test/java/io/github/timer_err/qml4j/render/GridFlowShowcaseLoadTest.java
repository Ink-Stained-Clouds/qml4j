package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.layout.Flow;
import io.github.timer_err.qml4j.render.items.layout.GridLayout;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The GridLayout/Flow showcase loads end to end: GridLayout cells with span/fill
// place under a constrained width, and the Flow tags wrap. A regression in either
// layout's registration or layout() surfaces here.
class GridFlowShowcaseLoadTest {

    private static byte[] res(String path) {
        try (InputStream in = GridFlowShowcaseLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static GridLayout findGrid(Item node) {
        if (node instanceof GridLayout) return (GridLayout) node;
        for (Item c : node.children) {
            GridLayout g = findGrid(c);
            if (g != null) return g;
        }
        return null;
    }

    private static Flow findFlow(Item node) {
        if (node instanceof Flow) return (Flow) node;
        for (Item c : node.children) {
            Flow f = findFlow(c);
            if (f != null) return f;
        }
        return null;
    }

    @Test
    void loadsGridFlowShowcase() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(new String(res("showcases/GridFlowShowcase.qml"), StandardCharsets.UTF_8));
        root.width.set(720.0);
        root.height.set(720.0);

        // Settle bindings (so the layouts' `width: parent.width - 32` resolves) but
        // not the renderer's measure pass -- text measurement needs native Skija,
        // which is absent headless. The cell sizes here come from QML literals, so
        // driving layout() directly is enough to exercise placement.
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        GridLayout grid = findGrid(root);
        assertNotNull(grid, "GridLayout instantiated");
        grid.layout();
        assertTrue(grid.implicitHeight.peek().doubleValue() > 0, "grid sized its rows");

        // The header straddles all 3 columns: its width spans the whole grid.
        Item header = grid.children.get(0);
        assertEquals(grid.implicitWidth.peek().doubleValue(),
            header.width.peek().doubleValue(), 1.0, "columnSpan:3 header spans the grid");

        Flow flow = findFlow(root);
        assertNotNull(flow, "Flow instantiated");
        flow.layout();
        // 9 tags wider than the ~688px Flow must wrap onto more than one line.
        double maxY = 0;
        for (Item c : flow.children) maxY = Math.max(maxY, c.y.peek().doubleValue());
        assertTrue(maxY > 0, "Flow tags wrapped to a second line");
    }
}
