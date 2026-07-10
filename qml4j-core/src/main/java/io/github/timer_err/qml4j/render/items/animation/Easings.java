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
            case 9:  return t * t * t * t;                                  // InQuart
            case 10: { double f = 1.0 - t; return 1.0 - f * f * f * f; }    // OutQuart
            case 11: return t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2; // InOutQuart
            case 13: return 1.0 - Math.cos((t * Math.PI) / 2);             // InSine
            case 14: return Math.sin((t * Math.PI) / 2);                   // OutSine
            case 15: return -(Math.cos(Math.PI * t) - 1) / 2;             // InOutSine
            // Back = overshoot curves (Qt default overshoot 1.70158). OutBack is the
            // springy ease-out used by AMLL/SPlayer's cover morph (cubic-bezier
            // 0.34,1.56,0.64,1). c3 = c1 + 1.
            case 29: { double c1 = 1.70158, c3 = c1 + 1; return c3 * t * t * t - c1 * t * t; } // InBack
            case 30: { double c1 = 1.70158, c3 = c1 + 1, f = t - 1;
                       return 1.0 + c3 * f * f * f + c1 * f * f; }           // OutBack
            case 31: { double c2 = 1.70158 * 1.525;                         // InOutBack
                       return t < 0.5
                           ? (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                           : (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (2 * t - 2) + c2) + 2) / 2; }
            default: return t;                                             // Linear + unmapped
        }
    }
}
