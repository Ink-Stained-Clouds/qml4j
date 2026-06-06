package io.qml4j.render.items.layout;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// QtQuick.Layouts GridLayout. Children are placed into a cell grid: each takes its
// Layout.row/column if set, else flows into the next free cell (LeftToRight wraps
// at `columns`, TopToBottom wraps at `rows`). Column widths/row heights size to the
// largest non-spanning child in each track; Layout.rowSpan/columnSpan let a child
// straddle several. A child with Layout.fillWidth/fillHeight fills its cell box;
// otherwise it keeps its natural size, aligned within the cell (default: centred).
public class GridLayout extends Item {
    public final Property<Number> columns = new Property<>(-1);
    public final Property<Number> rows = new Property<>(-1);
    public final Property<Number> rowSpacing = new Property<>(0);
    public final Property<Number> columnSpacing = new Property<>(0);
    public final Property<Number> flow = new Property<>(0); // 0 LeftToRight, 1 TopToBottom

    private static final class Cell {
        final Item item;
        int row, col, rowSpan, colSpan;
        Cell(Item item) { this.item = item; }
    }

    @Override
    public void layout() {
        List<Cell> cells = assignCells();
        if (cells.isEmpty()) { implicitWidth.set(0); implicitHeight.set(0); return; }

        int nCols = 0, nRows = 0;
        for (Cell c : cells) {
            nCols = Math.max(nCols, c.col + c.colSpan);
            nRows = Math.max(nRows, c.row + c.rowSpan);
        }

        double[] colW = trackSizes(cells, nCols, true);
        double[] rowH = trackSizes(cells, nRows, false);
        distributeExtra(cells, colW, nCols, true, width.peek().doubleValue(),
            columnSpacing.peek().doubleValue());
        distributeExtra(cells, rowH, nRows, false, height.peek().doubleValue(),
            rowSpacing.peek().doubleValue());

        double[] colX = offsets(colW, columnSpacing.peek().doubleValue());
        double[] rowY = offsets(rowH, rowSpacing.peek().doubleValue());

        for (Cell c : cells) place(c, colX, colW, rowY, rowH);

        implicitWidth.set(extent(colW, columnSpacing.peek().doubleValue()));
        implicitHeight.set(extent(rowH, rowSpacing.peek().doubleValue()));
    }

    private List<Cell> assignCells() {
        boolean ltr = flow.peek().intValue() == 0;
        int colLimit = limit(columns.peek().intValue(), ltr);
        int rowLimit = limit(rows.peek().intValue(), !ltr);
        Set<Long> occupied = new HashSet<>();
        int[] cursor = {0, 0};
        List<Cell> cells = new ArrayList<>();
        for (Item it : children) {
            if (!it.visible.peek()) continue;
            Cell cell = new Cell(it);
            cell.rowSpan = Math.max(1, it.Layout.rowSpan.peek().intValue());
            cell.colSpan = Math.max(1, it.Layout.columnSpan.peek().intValue());
            int er = it.Layout.row.peek().intValue();
            int ec = it.Layout.column.peek().intValue();
            if (er >= 0 && ec >= 0) {
                cell.row = er;
                cell.col = ec;
            } else {
                placeAuto(cell, occupied, cursor, colLimit, rowLimit, ltr);
            }
            occupy(occupied, cell);
            cells.add(cell);
        }
        return cells;
    }

    // The wrap track count along the flow's primary axis; an unset (<=0) limit on
    // that axis means "never wrap" (everything on one line).
    private static int limit(int value, boolean isPrimaryAxis) {
        if (!isPrimaryAxis) return Integer.MAX_VALUE;
        return value > 0 ? value : Integer.MAX_VALUE;
    }

    private void placeAuto(Cell cell, Set<Long> occupied, int[] cursor,
                           int colLimit, int rowLimit, boolean ltr) {
        while (true) {
            int r = cursor[0], c = cursor[1];
            boolean overflow = ltr ? c + cell.colSpan > colLimit
                                   : r + cell.rowSpan > rowLimit;
            if (!overflow && fits(occupied, r, c, cell.rowSpan, cell.colSpan)) {
                cell.row = r;
                cell.col = c;
                advance(cursor, cell, colLimit, rowLimit, ltr, true);
                return;
            }
            advance(cursor, cell, colLimit, rowLimit, ltr, false);
        }
    }

    // Step the auto-placement cursor. After placing a cell (`past`) it jumps to the
    // end of that cell's span; while probing for a free slot it moves one cell.
    private static void advance(int[] cursor, Cell cell, int colLimit, int rowLimit,
                                boolean ltr, boolean past) {
        if (ltr) {
            cursor[1] += past ? cell.colSpan : 1;
            if (cursor[1] >= colLimit) { cursor[1] = 0; cursor[0]++; }
        } else {
            cursor[0] += past ? cell.rowSpan : 1;
            if (cursor[0] >= rowLimit) { cursor[0] = 0; cursor[1]++; }
        }
    }

    private static boolean fits(Set<Long> occupied, int row, int col, int rs, int cs) {
        for (int r = row; r < row + rs; r++)
            for (int c = col; c < col + cs; c++)
                if (occupied.contains(key(r, c))) return false;
        return true;
    }

    private static void occupy(Set<Long> occupied, Cell cell) {
        for (int r = cell.row; r < cell.row + cell.rowSpan; r++)
            for (int c = cell.col; c < cell.col + cell.colSpan; c++)
                occupied.add(key(r, c));
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xffffffffL);
    }

    // Track (column or row) base sizes: each single-span child grows its track to
    // its natural main size; a spanning child then widens its tracks just enough to
    // fit, splitting any deficit evenly across the tracks it covers.
    private double[] trackSizes(List<Cell> cells, int n, boolean horizontal) {
        double[] size = new double[n];
        for (Cell c : cells) {
            int span = horizontal ? c.colSpan : c.rowSpan;
            if (span != 1) continue;
            int idx = horizontal ? c.col : c.row;
            size[idx] = Math.max(size[idx], natural(c.item, horizontal));
        }
        double gap = (horizontal ? columnSpacing : rowSpacing).peek().doubleValue();
        for (Cell c : cells) {
            int span = horizontal ? c.colSpan : c.rowSpan;
            if (span == 1) continue;
            int idx = horizontal ? c.col : c.row;
            double have = gap * (span - 1);
            for (int i = 0; i < span; i++) have += size[idx + i];
            double need = natural(c.item, horizontal);
            if (need > have) {
                double add = (need - have) / span;
                for (int i = 0; i < span; i++) size[idx + i] += add;
            }
        }
        return size;
    }

    private static double natural(Item it, boolean horizontal) {
        LayoutAttached la = it.Layout;
        if (horizontal)
            return LayoutSizing.mainSize(la.preferredWidth, it.implicitWidth, it.width,
                Boolean.TRUE.equals(la.fillWidth.peek()));
        return LayoutSizing.mainSize(la.preferredHeight, it.implicitHeight, it.height,
            Boolean.TRUE.equals(la.fillHeight.peek()));
    }

    // When the grid is given more room than its content needs, hand the slack to the
    // tracks that contain a filling child, split evenly.
    private void distributeExtra(List<Cell> cells, double[] size, int n,
                                 boolean horizontal, double own, double gap) {
        double content = extent(size, gap);
        if (own <= content) return;
        boolean[] fill = new boolean[n];
        int count = 0;
        for (Cell c : cells) {
            int span = horizontal ? c.colSpan : c.rowSpan;
            boolean f = horizontal ? Boolean.TRUE.equals(c.item.Layout.fillWidth.peek())
                                   : Boolean.TRUE.equals(c.item.Layout.fillHeight.peek());
            if (span == 1 && f) {
                int idx = horizontal ? c.col : c.row;
                if (!fill[idx]) { fill[idx] = true; count++; }
            }
        }
        if (count == 0) return;
        double extra = (own - content) / count;
        for (int i = 0; i < n; i++) if (fill[i]) size[i] += extra;
    }

    private static double[] offsets(double[] size, double gap) {
        double[] off = new double[size.length];
        double acc = 0;
        for (int i = 0; i < size.length; i++) {
            off[i] = acc;
            acc += size[i] + gap;
        }
        return off;
    }

    private static double extent(double[] size, double gap) {
        if (size.length == 0) return 0;
        double acc = -gap;
        for (double v : size) acc += v + gap;
        return acc;
    }

    private void place(Cell cell, double[] colX, double[] colW, double[] rowY, double[] rowH) {
        Item it = cell.item;
        LayoutAttached la = it.Layout;
        double cellX = colX[cell.col];
        double cellY = rowY[cell.row];
        double cellW = span(colW, cell.col, cell.colSpan, columnSpacing.peek().doubleValue());
        double cellH = span(rowH, cell.row, cell.rowSpan, rowSpacing.peek().doubleValue());

        if (Boolean.TRUE.equals(la.fillWidth.peek())) {
            it.x.set(cellX);
            it.width.set(cellW);
        } else {
            double w = natural(it, true);
            it.width.set(w);
            it.x.set(cellX + alignOffset(la.alignment.peek().intValue(), cellW, w, true));
        }
        if (Boolean.TRUE.equals(la.fillHeight.peek())) {
            it.y.set(cellY);
            it.height.set(cellH);
        } else {
            double h = natural(it, false);
            it.height.set(h);
            it.y.set(cellY + alignOffset(la.alignment.peek().intValue(), cellH, h, false));
        }
    }

    private static double span(double[] size, int idx, int n, double gap) {
        double total = gap * (n - 1);
        for (int i = 0; i < n; i++) total += size[idx + i];
        return total;
    }

    private static double alignOffset(int alignment, double box, double size, boolean horizontal) {
        if (horizontal) {
            if ((alignment & 1) != 0) return 0;            // AlignLeft
            if ((alignment & 2) != 0) return box - size;   // AlignRight
        } else {
            if ((alignment & 32) != 0) return 0;           // AlignTop
            if ((alignment & 64) != 0) return box - size;  // AlignBottom
        }
        return (box - size) / 2;
    }
}
