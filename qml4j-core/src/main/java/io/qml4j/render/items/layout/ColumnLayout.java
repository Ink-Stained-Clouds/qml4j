package io.qml4j.render.items.layout;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

// QtQuick.Layouts ColumnLayout. Top-to-bottom mirror of RowLayout: main axis is
// vertical (Layout.preferredHeight, top/bottom margins, fillHeight share extra),
// cross axis is horizontal (fillWidth stretches, else aligned/centred).
public class ColumnLayout extends Item {
    public final Property<Number> spacing = new Property<>(0);

    @Override
    public void layout() {
        List<Item> vis = new ArrayList<>();
        for (Item c : children) if (c.isVisible()) vis.add(c);
        if (vis.isEmpty()) { implicitWidth.set(0); implicitHeight.set(0); return; }

        double s = spacing.peekDouble();
        int n = vis.size();
        double[] h = new double[n];
        double[] top = new double[n];
        double[] bottom = new double[n];
        boolean[] fill = new boolean[n];
        double sumMain = s * (n - 1);
        double maxCross = 0;
        int fillCount = 0;

        for (int i = 0; i < n; i++) {
            Item c = vis.get(i);
            LayoutAttached la = c.Layout;
            fill[i] = Boolean.TRUE.equals(la.fillHeight.peek());
            h[i] = LayoutSizing.mainSize(la.preferredHeight, c.implicitHeight, c.height, fill[i]);
            top[i] = LayoutSizing.margin(la.topMargin, la.margins);
            bottom[i] = LayoutSizing.margin(la.bottomMargin, la.margins);
            if (fill[i]) fillCount++;
            sumMain += top[i] + h[i] + bottom[i];
            double cross = LayoutSizing.margin(la.leftMargin, la.margins)
                + LayoutSizing.crossSize(la.preferredWidth, c.implicitWidth, c.width)
                + LayoutSizing.margin(la.rightMargin, la.margins);
            if (cross > maxCross) maxCross = cross;
        }

        implicitWidth.set(maxCross);
        implicitHeight.set(sumMain);

        // When the layout's own width is constrained (anchored/explicit), fill and
        // alignment must use THAT width, not the widest child's natural size --
        // else a long unwrapped label balloons the box and pushes aligned items
        // (centred icon, right-aligned actions) off the real bounds.
        double ownW = width.peekDouble();
        double boxW = ownW > 0 ? ownW : maxCross;
        double boxH = Math.max(height.peekDouble(), sumMain);
        if (fillCount > 0 && boxH > sumMain) {
            double extra = (boxH - sumMain) / fillCount;
            for (int i = 0; i < n; i++) if (fill[i]) h[i] += extra;
        }

        double y = 0;
        for (int i = 0; i < n; i++) {
            Item c = vis.get(i);
            LayoutAttached la = c.Layout;
            y += top[i];
            c.y.set(y);
            c.height.set(h[i]);
            double leftM = LayoutSizing.margin(la.leftMargin, la.margins);
            double rightM = LayoutSizing.margin(la.rightMargin, la.margins);
            if (Boolean.TRUE.equals(la.fillWidth.peek())) {
                c.x.set(leftM);
                c.width.set(boxW - leftM - rightM);
            } else {
                double cw = LayoutSizing.crossSize(la.preferredWidth, c.implicitWidth, c.width);
                int align = la.alignment.peekInt();
                if (align == 0) {
                    // Qt default (no fillWidth, no Layout.alignment): the child starts at
                    // the left of the column -- it is NOT centred. Upstream sets
                    // Qt.AlignHCenter explicitly where it wants centring; the absence of
                    // alignment means left. A zero-width child (a bare container holding
                    // anchored content, e.g. a drawer header with a left-anchored title)
                    // additionally fills the width so its anchored children resolve
                    // against the full column rather than a collapsed 0 box.
                    c.x.set(leftM);
                    if (cw <= 0) c.width.set(boxW - leftM - rightM);
                } else {
                    c.x.set(LayoutSizing.crossPos(align, boxW, cw, leftM, rightM, true));
                }
            }
            y += h[i] + bottom[i] + s;
        }
    }
}
