package io.qml4j.runtime.qt;

// A color value: channels in 0..1, read as .r/.g/.b/.a. Parses the QML hex
// forms (#rgb / #rrggbb / #aarrggbb) and formats back to #aarrggbb. The
// channels and the parse/format rules live here rather than scattered as
// free functions over raw strings.
public final class QColor {
    public final double r;
    public final double g;
    public final double b;
    public final double a;

    public QColor(double r, double g, double b, double a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    // Parse #rgb / #rrggbb / #aarrggbb into 0..1 channels. A malformed value
    // yields opaque black, matching Qt.color's lenient behaviour.
    public static QColor parse(String raw) {
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        long bits;
        try {
            bits = Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return new QColor(0, 0, 0, 1);
        }
        int r, g, b, a = 255;
        if (s.length() == 3) {
            r = (int) ((bits >> 8) & 0xF) * 17;
            g = (int) ((bits >> 4) & 0xF) * 17;
            b = (int) (bits & 0xF) * 17;
        } else if (s.length() == 8) {
            a = (int) ((bits >> 24) & 0xFF);
            r = (int) ((bits >> 16) & 0xFF);
            g = (int) ((bits >> 8) & 0xFF);
            b = (int) (bits & 0xFF);
        } else {
            r = (int) ((bits >> 16) & 0xFF);
            g = (int) ((bits >> 8) & 0xFF);
            b = (int) (bits & 0xFF);
        }
        return new QColor(r / 255.0, g / 255.0, b / 255.0, a / 255.0);
    }

    public String toHex() {
        return String.format("#%02x%02x%02x%02x",
            toByte(a), toByte(r), toByte(g), toByte(b));
    }

    private static int toByte(double c) {
        return (int) Math.round(c * 255.0);
    }
}
