package io.github.timer_err.qml4j.runtime.color;

// Ported from Google's material-color-utilities (Apache 2.0). ARGB <-> XYZ <-> L*
// conversions underpinning HCT.
final class ColorUtils {

    private ColorUtils() {}

    private static final double[][] SRGB_TO_XYZ = {
        {0.41233895, 0.35762064, 0.18051042},
        {0.2126, 0.7152, 0.0722},
        {0.01932141, 0.11916382, 0.95034478},
    };

    private static final double[][] XYZ_TO_SRGB = {
        {3.2413774792388685, -1.5376652402851851, -0.49885366846268053},
        {-0.9691452513005321, 1.8758853451067872, 0.04156585616912061},
        {0.05562093689691305, -0.20395524564742123, 1.0571799111220335},
    };

    private static final double[] WHITE_POINT_D65 = {95.047, 100.0, 108.883};

    static int argbFromRgb(int red, int green, int blue) {
        return (255 << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    @SuppressWarnings("unused")
    static int argbFromLinrgb(double[] linrgb) {
        int r = delinearized(linrgb[0]);
        int g = delinearized(linrgb[1]);
        int b = delinearized(linrgb[2]);
        return argbFromRgb(r, g, b);
    }

    static int redFromArgb(int argb) {
        return (argb >> 16) & 255;
    }

    static int greenFromArgb(int argb) {
        return (argb >> 8) & 255;
    }

    static int blueFromArgb(int argb) {
        return argb & 255;
    }

    static int argbFromXyz(double x, double y, double z) {
        double[][] matrix = XYZ_TO_SRGB;
        double linearR = matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z;
        double linearG = matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z;
        double linearB = matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z;
        int r = delinearized(linearR);
        int g = delinearized(linearG);
        int b = delinearized(linearB);
        return argbFromRgb(r, g, b);
    }

    static double[] xyzFromArgb(int argb) {
        double r = linearized(redFromArgb(argb));
        double g = linearized(greenFromArgb(argb));
        double b = linearized(blueFromArgb(argb));
        return MathUtils.matrixMultiply(new double[] {r, g, b}, SRGB_TO_XYZ);
    }

    static double yFromLstar(double lstar) {
        return 100.0 * labInvf((lstar + 16.0) / 116.0);
    }

    static double lstarFromY(double y) {
        return labF(y / 100.0) * 116.0 - 16.0;
    }

    // 0-255 sRGB component -> 0-100 linear RGB.
    static double linearized(int rgbComponent) {
        double normalized = rgbComponent / 255.0;
        if (normalized <= 0.040449936) {
            return normalized / 12.92 * 100.0;
        } else {
            return Math.pow((normalized + 0.055) / 1.055, 2.4) * 100.0;
        }
    }

    static int delinearized(double rgbComponent) {
        double normalized = rgbComponent / 100.0;
        double delinearized;
        if (normalized <= 0.0031308) {
            delinearized = normalized * 12.92;
        } else {
            delinearized = 1.055 * Math.pow(normalized, 1.0 / 2.4) - 0.055;
        }
        return MathUtils.clampInt(0, 255, (int) Math.round(delinearized * 255.0));
    }

    static double[] whitePointD65() {
        return WHITE_POINT_D65;
    }

    static double lstarFromArgb(int argb) {
        double y = xyzFromArgb(argb)[1];
        return lstarFromY(y);
    }

    static int argbFromLstar(double lstar) {
        double y = yFromLstar(lstar);
        int component = delinearized(y);
        return argbFromRgb(component, component, component);
    }

    private static double labF(double t) {
        double e = 216.0 / 24389.0;
        double kappa = 24389.0 / 27.0;
        if (t > e) {
            return Math.cbrt(t);
        } else {
            return (kappa * t + 16) / 116;
        }
    }

    private static double labInvf(double ft) {
        double e = 216.0 / 24389.0;
        double kappa = 24389.0 / 27.0;
        double ft3 = ft * ft * ft;
        if (ft3 > e) {
            return ft3;
        } else {
            return (116 * ft - 16) / kappa;
        }
    }
}
