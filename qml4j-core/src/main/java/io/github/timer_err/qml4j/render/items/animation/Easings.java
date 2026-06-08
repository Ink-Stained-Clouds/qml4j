package io.github.timer_err.qml4j.render.items.animation;

final class Easings {
    private Easings() {}

    // QEasingCurve::Type ordinals (see ExpressionCodegen ENUMS "Easing").
    // Unmapped ordinals fall through to linear.
    static double apply(int type, double t) {
        switch (type) {
            case 1:  return t * t;                                          // InQuad
            case 2:  return 1.0 - (1.0 - t) * (1.0 - t);                    // OutQuad
            case 3:  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2; // InOutQuad
            case 5:  return t * t * t;                                      // InCubic
            case 6:  { double f = 1.0 - t; return 1.0 - f * f * f; }        // OutCubic
            case 7:  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; // InOutCubic
            case 13: return 1.0 - Math.cos((t * Math.PI) / 2);             // InSine
            case 14: return Math.sin((t * Math.PI) / 2);                   // OutSine
            case 15: return -(Math.cos(Math.PI * t) - 1) / 2;             // InOutSine
            default: return t;                                             // Linear + unmapped
        }
    }
}
