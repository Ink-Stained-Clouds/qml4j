package io.github.timer_err.qml4j.render.items.layout;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;

// QtQuick.Layouts ColumnLayout. Top-to-bottom mirror of RowLayout: main axis is
// vertical (Layout.preferredHeight, top/bottom margins, fillHeight share extra),
// cross axis is horizontal (fillWidth stretches, else aligned/centred).
public class ColumnLayout extends Item {
    public final Property<Number> spacing = new Property<>(0);

    // Reused scratch, grown on demand — layout() runs every settle pass for every
    // container (the 5 Hz play clock re-settles the whole tree), so the old per-call
    // ArrayList + four arrays were a steady GC-pressure source. layout() is never
    // re-entrant on the same instance, so plain instance fields are safe.
    private Item[] vis = new Item[0];
    private double[] h = new double[0];
    private double[] top = new double[0];
    private double[] bottom = new double[0];
    private boolean[] fill = new boolean[0];

    private void ensureCap(int cap) {
        if (vis.length >= cap) return;
        vis = new Item[cap];
        h = new double[cap];
        top = new double[cap];
        bottom = new double[cap];
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
        double[] h = this.h, top = this.top, bottom = this.bottom;
        boolean[] fill = this.fill;
        double sumMain = s * (n - 1);
        double maxCross = 0;
        int fillCount = 0;

        for (int i = 0; i < n; i++) {
            Item c = vis[i];
            LayoutAttached la = c.Layout;
            fill[i] = Boolean.TRUE.equals(la.fillHeight.peek());
            h[i] = LayoutSizing.mainSize(la.preferredHeight, c.implicitHeight, c.height, fill[i]);
            top[i] = LayoutSizing.margin(la.topMargin, la.margins);
            bottom[i] = LayoutSizing.margin(la.bottomMargin, la.margins);
            if (fill[i]) fillCount++;
            sumMain += top[i] + h[i] + bottom[i];
            double cross = LayoutSizing.margin(la.leftMargin, la.margins)
                + LayoutSizing.capMax(LayoutSizing.crossSize(la.preferredWidth, c.implicitWidth, c.width,
                    Boolean.TRUE.equals(la.fillWidth.peek())), la.maximumWidth)
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
        // Constrained shorter than content (an outer layout/anchor set our height below
        // sumMain): fillHeight children SHRINK to absorb the deficit -- a fillHeight chart
        // in a fixed-height card fits instead of overflowing. When taller, they grow.
        double availH = height.peekDouble();
        double boxH = availH > 0 ? availH : sumMain;
        if (fillCount > 0 && boxH != sumMain) {
            double delta = (boxH - sumMain) / fillCount;
            for (int i = 0; i < n; i++) if (fill[i]) h[i] = Math.max(0, h[i] + delta);
        }

        double y = 0;
        for (int i = 0; i < n; i++) {
            Item c = vis[i];
            LayoutAttached la = c.Layout;
            y += top[i];
            c.y.set(y);
            c.height.set(h[i]);
            double leftM = LayoutSizing.margin(la.leftMargin, la.margins);
            double rightM = LayoutSizing.margin(la.rightMargin, la.margins);
            if (Boolean.TRUE.equals(la.fillWidth.peek())) {
                // fillWidth fills the column, but Layout.maximumWidth caps it (a square widget
                // stays square) and -- with no cap but a horizontal Layout.alignment -- it
                // shrinks to its content and aligns instead (Qt: a centred fillWidth RowLayout
                // of buttons centres as a group rather than left-packing across the full width).
                double avail = boxW - leftM - rightM;
                int align = la.alignment.peekInt();
                double target = avail;
                double maxW = la.maximumWidth.peekDouble();
                if (maxW >= 0) {
                    if (target > maxW) target = maxW;
                } else if ((align & (1 | 2 | 4)) != 0 && c.implicitWidth.peekDouble() > 0) {
                    // Shrink-to-content + align only when the child has a real content width (a
                    // centred RowLayout of buttons). A fillWidth child with no implicit width --
                    // a plain Item whose content anchors to fill (a NavigationRail item whose
                    // pill is `parent.width - 24`) -- must FILL, not collapse to a stale size.
                    double impl = c.implicitWidth.peekDouble();
                    if (impl < target) target = impl;
                }
                c.width.set(target);
                // A constrained fillWidth child starts at the left unless it has an explicit
                // horizontal alignment (Qt). crossPos would centre on no-alignment (the v0
                // divergence) -- so a maximumWidth-capped section with no alignment must not
                // centre here.
                double pos = leftM;
                if (target < avail) {
                    if ((align & 2) != 0) pos = boxW - rightM - target;            // AlignRight
                    else if ((align & 4) != 0) pos = leftM + (avail - target) / 2; // AlignHCenter
                }
                c.x.set(pos);
            } else {
                double cw = LayoutSizing.crossSize(la.preferredWidth, c.implicitWidth, c.width);
                double capped = LayoutSizing.capMax(cw, la.maximumWidth);
                if (capped < cw) c.width.set(capped); // enforce the cap so an eliding label honours it
                cw = capped;
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
