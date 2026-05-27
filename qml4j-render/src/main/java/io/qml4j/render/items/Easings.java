package io.qml4j.render.items;

final class Easings {
    private Easings() {}

    static double apply(String name, double t) {
        if (name == null || "linear".equals(name)) return t;
        switch (name) {
            case "easeInQuad":  return t * t;
            case "easeOutQuad": return 1.0 - (1.0 - t) * (1.0 - t);
            case "easeInOutQuad":
                return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            default: return t;
        }
    }
}
