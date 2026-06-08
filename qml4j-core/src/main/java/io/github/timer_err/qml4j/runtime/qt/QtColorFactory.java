package io.github.timer_err.qml4j.runtime.qt;

import io.github.timer_err.qml4j.runtime.convert.Coercion;

// The Qt.color / Qt.rgba / Qt.hsla constructors. color() yields a QColor
// (its channels are read as .r/.g/.b/.a); rgba()/hsla() yield the #aarrggbb
// string the rest of the pipeline consumes.
public final class QtColorFactory {

    private QtColorFactory() {}

    public static Object qtColor(Object v) {
        if (v instanceof QColor) return v;
        return QColor.parse(String.valueOf(v));
    }

    public static Object qtRgba(Object r, Object g, Object b, Object a) {
        int ri = clampByte((int) Math.round(Coercion.toNumber(r) * 255.0));
        int gi = clampByte((int) Math.round(Coercion.toNumber(g) * 255.0));
        int bi = clampByte((int) Math.round(Coercion.toNumber(b) * 255.0));
        int ai = clampByte((int) Math.round(Coercion.toNumber(a) * 255.0));
        return new QColor(ri / 255.0, gi / 255.0, bi / 255.0, ai / 255.0).toHex();
    }

    public static Object qtHsla(Object h, Object s, Object l, Object a) {
        double[] rgb = hslToRgb(
            wrap01(Coercion.toNumber(h)),
            clamp01(Coercion.toNumber(s)),
            clamp01(Coercion.toNumber(l)));
        double aa = clamp01(Coercion.toNumber(a));
        return new QColor(rgb[0], rgb[1], rgb[2], aa).toHex();
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double wrap01(double v) {
        double w = v - Math.floor(v);
        return w < 0 ? w + 1 : w;
    }

    private static double[] hslToRgb(double h, double s, double l) {
        if (s == 0.0) return new double[]{l, l, l};
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        return new double[]{
            hueToRgb(p, q, h + 1.0 / 3.0),
            hueToRgb(p, q, h),
            hueToRgb(p, q, h - 1.0 / 3.0)
        };
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6;
        return p;
    }
}
