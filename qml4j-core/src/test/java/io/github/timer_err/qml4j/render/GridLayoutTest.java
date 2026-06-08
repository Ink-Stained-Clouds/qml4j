package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.layout.GridLayout;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GridLayoutTest {
    private static Rectangle cell(double w, double h) {
        Rectangle r = new Rectangle();
        r.implicitWidth.set(w);
        r.implicitHeight.set(h);
        return r;
    }

    private static void add(GridLayout g, Rectangle r) {
        g.children.add(r);
        r.parent.set(g);
    }

    @Test
    void flowsRowMajorWrappingAtColumns() {
        GridLayout g = new GridLayout();
        g.columns.set(2);
        Rectangle[] r = new Rectangle[4];
        for (int i = 0; i < 4; i++) { r[i] = cell(30, 20); add(g, r[i]); }
        g.layout();

        assertEquals(0.0, r[0].x.peek().doubleValue(), 1e-6);
        assertEquals(0.0, r[0].y.peek().doubleValue(), 1e-6);
        assertEquals(30.0, r[1].x.peek().doubleValue(), 1e-6);
        assertEquals(0.0, r[1].y.peek().doubleValue(), 1e-6);
        assertEquals(0.0, r[2].x.peek().doubleValue(), 1e-6);
        assertEquals(20.0, r[2].y.peek().doubleValue(), 1e-6, "third item wraps to row 1");
        assertEquals(30.0, r[3].x.peek().doubleValue(), 1e-6);
        assertEquals(20.0, r[3].y.peek().doubleValue(), 1e-6);
        assertEquals(60.0, g.implicitWidth.peek().doubleValue(), 1e-6);
        assertEquals(40.0, g.implicitHeight.peek().doubleValue(), 1e-6);
    }

    @Test
    void fillWidthColumnsShareExtraSpace() {
        GridLayout g = new GridLayout();
        g.columns.set(2);
        g.width.set(200.0);
        Rectangle a = cell(30, 20);
        Rectangle b = cell(30, 20);
        a.Layout.fillWidth.set(Boolean.TRUE);
        b.Layout.fillWidth.set(Boolean.TRUE);
        add(g, a);
        add(g, b);
        g.layout();

        assertEquals(100.0, a.width.peek().doubleValue(), 1e-6, "extra 140 split across 2 fill cols");
        assertEquals(100.0, b.width.peek().doubleValue(), 1e-6);
        assertEquals(0.0, a.x.peek().doubleValue(), 1e-6);
        assertEquals(100.0, b.x.peek().doubleValue(), 1e-6);
    }

    @Test
    void columnSpanWidensSpannedTracks() {
        GridLayout g = new GridLayout();
        g.columns.set(2);
        Rectangle wide = cell(100, 20);
        wide.Layout.columnSpan.set(2);
        Rectangle x = cell(30, 20);
        Rectangle y = cell(30, 20);
        add(g, wide);
        add(g, x);
        add(g, y);
        g.layout();

        // single-span cells give each column a base of 30; the 100-wide span needs
        // 40 more, split evenly -> each column becomes 50. The 30-wide non-fill
        // children then centre within their 50-wide column (offset 10).
        assertEquals(10.0, x.x.peek().doubleValue(), 1e-6);
        assertEquals(60.0, y.x.peek().doubleValue(), 1e-6, "column 1 widened to fit the span");
        assertEquals(100.0, g.implicitWidth.peek().doubleValue(), 1e-6);
        assertEquals(40.0, g.implicitHeight.peek().doubleValue(), 1e-6, "span on row 0, x/y on row 1");
    }

    @Test
    void nonFillChildAlignsWithinCell() {
        GridLayout g = new GridLayout();
        g.columns.set(1);
        Rectangle wide = cell(100, 20);
        Rectangle small = cell(30, 20);
        small.Layout.alignment.set(2); // Qt.AlignRight
        add(g, wide);
        add(g, small);
        g.layout();

        assertEquals(70.0, small.x.peek().doubleValue(), 1e-6, "right-aligned within the 100-wide column");
        assertEquals(30.0, small.width.peek().doubleValue(), 1e-6, "keeps its natural width");
    }

    @Test
    void explicitRowColumnPlacement() {
        GridLayout g = new GridLayout();
        g.columns.set(3);
        Rectangle a = cell(30, 20);
        a.Layout.row.set(0);
        a.Layout.column.set(0);
        Rectangle b = cell(30, 20);
        b.Layout.row.set(0);
        b.Layout.column.set(2);
        Rectangle c = cell(30, 20);
        c.Layout.row.set(1);
        c.Layout.column.set(1);
        add(g, a);
        add(g, b);
        add(g, c);
        g.layout();

        assertEquals(60.0, b.x.peek().doubleValue(), 1e-6, "column 2 -> x = 2*30");
        assertEquals(0.0, b.y.peek().doubleValue(), 1e-6);
        assertEquals(30.0, c.x.peek().doubleValue(), 1e-6, "column 1 -> x = 30");
        assertEquals(20.0, c.y.peek().doubleValue(), 1e-6, "row 1 -> y = 20");
    }
}
