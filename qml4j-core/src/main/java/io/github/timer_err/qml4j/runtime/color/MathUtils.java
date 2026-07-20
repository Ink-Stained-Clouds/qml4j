package io.github.timer_err.qml4j.runtime.color;

// Ported from Google's material-color-utilities (Apache 2.0). Numeric helpers shared by
// the CAM16/HCT color science.
final class MathUtils {

    private MathUtils() {}

    static double lerp(double start, double stop, double amount) {
        return (1.0 - amount) * start + amount * stop;
    }

    static double clampDouble(double min, double max, double input) {
        return Math.max(min, Math.min(input, max));
    }

    static int clampInt(int min, int max, int input) {
        return Math.max(min, Math.min(input, max));
    }

    static double sanitizeDegreesDouble(double degrees) {
        degrees = degrees % 360.0;
        if (degrees < 0) degrees = degrees + 360.0;
        return degrees;
    }

    @SuppressWarnings("unused")
    static int sanitizeDegreesInt(int degrees) {
        degrees = degrees % 360;
        if (degrees < 0) degrees = degrees + 360;
        return degrees;
    }

    // 1.0 when rotating clockwise from `from` to `to`, -1.0 counterclockwise.
    @SuppressWarnings("unused")
    static double rotationDirection(double from, double to) {
        double increasingDifference = sanitizeDegreesDouble(to - from);
        return increasingDifference <= 180.0 ? 1.0 : -1.0;
    }

    @SuppressWarnings("unused")
    static double differenceDegrees(double a, double b) {
        return 180.0 - Math.abs(Math.abs(a - b) - 180.0);
    }

    static double[] matrixMultiply(double[] row, double[][] matrix) {
        double a = row[0] * matrix[0][0] + row[1] * matrix[0][1] + row[2] * matrix[0][2];
        double b = row[0] * matrix[1][0] + row[1] * matrix[1][1] + row[2] * matrix[1][2];
        double c = row[0] * matrix[2][0] + row[1] * matrix[2][1] + row[2] * matrix[2][2];
        return new double[] {a, b, c};
    }
}
