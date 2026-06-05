package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

// QtQuick.Layouts RowLayout. Lays children left-to-right using each child's
// Layout.preferredWidth (else implicitWidth/width), per-child horizontal margins
// and spacing; fillWidth children share any extra width. Cross-axis: fillHeight
// stretches, otherwise the child is aligned (default: vertically centred).
public class RowLayout extends Item {
    public final Property<Number> spacing = new Property<>(0);

    public void layout() {
        List<Item> vis = new ArrayList<>();
        for (Item c : children) if (c.visible.peek()) vis.add(c);
        if (vis.isEmpty()) { implicitWidth.set(0); implicitHeight.set(0); return; }

        double s = spacing.peek().doubleValue();
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
                + LayoutSizing.crossSize(la.preferredHeight, c.implicitHeight, c.height)
                + LayoutSizing.margin(la.bottomMargin, la.margins);
            if (cross > maxCross) maxCross = cross;
        }

        implicitWidth.set(sumMain);
        implicitHeight.set(maxCross);

        double boxW = Math.max(width.peek().doubleValue(), sumMain);
        double boxH = Math.max(height.peek().doubleValue(), maxCross);
        if (fillCount > 0 && boxW > sumMain) {
            double extra = (boxW - sumMain) / fillCount;
            for (int i = 0; i < n; i++) if (fill[i]) w[i] += extra;
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
                c.y.set(LayoutSizing.crossPos(la.alignment.peek().intValue(), boxH, ch, top, bottom, false));
            }
            x += w[i] + right[i] + s;
        }
    }
}
