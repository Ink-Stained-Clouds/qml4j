package io.qml4j.engine;

// Result of Qt.color(...): channel components in 0..1, read as .r/.g/.b/.a.
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
}
