package io.github.timer_err.qml4j.render.items.layout;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;

// QtQuick.Layouts RowLayout. Lays children left-to-right using each child's
// Layout.preferredWidth (else implicitWidth/width), per-child horizontal margins
// and spacing; fillWidth children share any extra width. Cross-axis: fillHeight
// stretches, otherwise the child is aligned (default: vertically centred).
public class RowLayout extends Item {
    public final Property<Number> spacing = new Property<>(0);

    // Reused scratch, grown on demand — see ColumnLayout: layout() runs every settle
    // pass for every container, so per-call arrays were steady GC pressure. Not
    // re-entrant on one instance, so plain instance fields are safe.
    private Item[] vis = new Item[0];
    private double[] w = new double[0];
    private double[] left = new double[0];
    private double[] right = new double[0];
    private boolean[] fill = new boolean[0];

    private void ensureCap(int cap) {
        if (vis.length >= cap) return;
        vis = new Item[cap];
        w = new double[cap];
        left = new double[cap];
        right = new double[cap];
        fill = new boolean[cap];
    }


    @Override
    public void layout() {
        int cc = children.size();
        ensureCap(cc);
        Item[] vis = this.vis;
        int n = 0;
        for (int i = 0; i < cc; i++) {
            Item c = children.get(i);
            if (c.isVisible()) vis[n++] = c;
        }
        if (n == 0) { implicitWidth.set(0); implicitHeight.set(0); return; }

        double s = spacing.peekDouble();
        double[] w = this.w, left = this.left, right = this.right;
        boolean[] fill = this.fill;
        double sumMain = s * (n - 1);
        double maxCross = 0;
        int fillCount = 0;

        for (int i = 0; i < n; i++) {
            Item c = vis[i];
            LayoutAttached la = c.Layout;
            fill[i] = Boolean.TRUE.equals(la.fillWidth.peek());
            w[i] = LayoutSizing.mainSize(c.Layout.preferredWidth, c.implicitWidth, c.width, fill[i]);
            left[i] = LayoutSizing.margin(la.leftMargin, la.margins);
            right[i] = LayoutSizing.margin(la.rightMargin, la.margins);
            if (fill[i]) fillCount++;
            sumMain += left[i] + w[i] + right[i];
            double cross = LayoutSizing.margin(la.topMargin, la.margins)
                + LayoutSizing.crossSize(la.preferredHeight, c.implicitHeight, c.height,
                    Boolean.TRUE.equals(la.fillHeight.peek()))
                + LayoutSizing.margin(la.bottomMargin, la.margins);
            if (cross > maxCross) maxCross = cross;
        }

        implicitWidth.set(sumMain);
        implicitHeight.set(maxCross);

        // When the layout is constrained narrower than its content (an outer layout/anchor
        // set our width below sumMain), fill children SHRINK to absorb the deficit -- a
        // fillWidth wrapping label then wraps instead of overflowing. When wider, they grow.
        double avail = width.peekDouble();
        double boxW = avail > 0 ? avail : sumMain;
        // Cross axis (height): when our own height is constrained (anchored/explicit), a
        // fillHeight child fills THAT, not the tallest child's natural size -- else a
        // fillHeight chart grows the cell past a fixed-height card and overflows.
        double availH = height.peekDouble();
        double boxH = availH > 0 ? availH : maxCross;
        if (fillCount > 0 && boxW != sumMain) {
            double delta = (boxW - sumMain) / fillCount;
            for (int i = 0; i < n; i++) if (fill[i]) w[i] = Math.max(0, w[i] + delta);
        }

        double x = 0;
        for (int i = 0; i < n; i++) {
            Item c = vis[i];
            LayoutAttached la = c.Layout;
            x += left[i];
            c.x.set(x);
            c.width.set(w[i]);
            double top = LayoutSizing.margin(la.topMargin, la.margins);
            double bottom = LayoutSizing.margin(la.bottomMargin, la.margins);
            if (Boolean.TRUE.equals(la.fillHeight.peek())) {
                c.y.set(top);
                c.height.set(boxH - top - bottom);
            } else {
                double ch = LayoutSizing.crossSize(la.preferredHeight, c.implicitHeight, c.height);
                int align = la.alignment.peekInt();
                if (ch <= 0 && align == 0) {
                    // No natural cross size and no explicit alignment: Qt stretches to
                    // fill the cell rather than collapsing to 0 height and centring.
                    c.y.set(top);
                    c.height.set(boxH - top - bottom);
                } else {
                    // Apply the cross size to the child, not just position it: a child
                    // with only Layout.preferredHeight (e.g. a bare Item holding a
                    // centred icon) would otherwise keep its 0 height and its centred
                    // content would collapse to the top edge.
                    c.height.set(ch);
                    c.y.set(LayoutSizing.crossPos(align, boxH, ch, top, bottom, false));
                }
            }
            x += w[i] + right[i] + s;
        }
    }
}
