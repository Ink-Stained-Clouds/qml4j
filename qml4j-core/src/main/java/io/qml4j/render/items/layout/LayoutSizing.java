package io.qml4j.render.items.layout;

import io.qml4j.engine.binding.Property;

// Shared sizing/margin/alignment helpers for RowLayout and ColumnLayout.
final class LayoutSizing {
    private LayoutSizing() {}

    // A child's intrinsic size along the MAIN axis: Layout.preferred* if set
    // (>=0), else its implicit size, else its explicit size. A fill child has NO
    // intrinsic main size -- its main size is an OUTPUT of the layout, so it must
    // never fall back to its (already-filled) explicit size, or the fill would
    // accumulate pass over pass (a spacer ratchets wider every frame).
    static double mainSize(Property<Number> preferred, Property<Number> implicit,
                           Property<Number> explicit, boolean isFill) {
        double pref = preferred.peek().doubleValue();
        if (pref >= 0) return pref;
        double iw = implicit.peek().doubleValue();
        if (iw > 0) return iw;
        return isFill ? 0 : explicit.peek().doubleValue();
    }

    static double crossSize(Property<Number> preferred, Property<Number> implicit,
                            Property<Number> explicit) {
        return sizeFor(preferred, implicit, explicit);
    }

    private static double sizeFor(Property<Number> preferred, Property<Number> implicit,
                                  Property<Number> explicit) {
        double pref = preferred.peek().doubleValue();
        if (pref >= 0) return pref;
        double iw = implicit.peek().doubleValue();
        return iw > 0 ? iw : explicit.peek().doubleValue();
    }

    // A specific margin (leftMargin/...) overrides the general `margins` only
    // when explicitly non-zero.
    static double margin(Property<Number> specific, Property<Number> general) {
        double m = specific.peek().doubleValue();
        return m != 0 ? m : general.peek().doubleValue();
    }

    // Cross-axis offset for a non-filling child. Defaults to centred (Qt's
    // default is stretch-to-fill; v0 centres instead — see divergence notes).
    static double crossPos(int alignment, double box, double size,
                           double startMargin, double endMargin, boolean crossIsHorizontal) {
        if (crossIsHorizontal) {
            if ((alignment & 1) != 0) return startMargin;             // AlignLeft
            if ((alignment & 2) != 0) return box - endMargin - size;  // AlignRight
        } else {
            if ((alignment & 32) != 0) return startMargin;            // AlignTop
            if ((alignment & 64) != 0) return box - endMargin - size; // AlignBottom
        }
        return (box - size) / 2;
    }
}
