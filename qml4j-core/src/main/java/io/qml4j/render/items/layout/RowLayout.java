package io.qml4j.render.items.layout;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

// QtQuick.Layouts RowLayout. Lays children left-to-right using each child's
// Layout.preferredWidth (else implicitWidth/width), per-child horizontal margins
// and spacing; fillWidth children share any extra width. Cross-axis: fillHeight
// stretches, otherwise the child is aligned (default: vertically centred).
public class RowLayout extends Item {
    public final Property<Number> spacing = new Property<>(0);

    @Override
    public void layout() {
        List<Item> vis = new ArrayList<>();
        for (Item c : children) if (c.isVisible()) vis.add(c);
        if (vis.isEmpty()) { implicitWidth.set(0); implicitHeight.set(0); return; }

        double s = spacing.peekDouble();
        int n = vis.size();
        double[] w = new double[n];
        double[] left = new double[n];
        double[] right = new double[n];
        boolean[] fill = new boolean[n];
        double sumMain = s * (n - 1);
        double maxCross = 0;
        int fillCount = 0;

        for (int i = 0; i < n; i++) {
            Item c = vis.get(i);
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
        double boxH = Math.max(height.peekDouble(), maxCross);
        if (fillCount > 0 && boxW != sumMain) {
            double delta = (boxW - sumMain) / fillCount;
            for (int i = 0; i < n; i++) if (fill[i]) w[i] = Math.max(0, w[i] + delta);
        }

        double x = 0;
        for (int i = 0; i < n; i++) {
            Item c = vis.get(i);
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
