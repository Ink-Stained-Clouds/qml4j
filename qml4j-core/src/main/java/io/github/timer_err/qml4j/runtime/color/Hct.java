package io.github.timer_err.qml4j.runtime.color;

// Ported from Google's material-color-utilities (Apache 2.0). HCT (Hue, Chroma, Tone):
// a perceptual color space where tone == L* maps directly to WCAG contrast. ARGB ->
// HCT is CAM16; HCT -> ARGB gamut-maps via the iterative CAM16 search below.
public final class Hct {

    private static final double DL_MAX = 0.2;
    private static final double DE_MAX = 1.0;
    private static final double LIGHTNESS_SEARCH_ENDPOINT = 0.01;

    private double hue;
    private double chroma;
    private double tone;
    private int argb;

    private Hct(int argb) {
        setInternalState(argb);
    }

    public static Hct fromInt(int argb) {
        return new Hct(argb);
    }

    public static Hct from(double hue, double chroma, double tone) {
        return new Hct(toArgb(hue, chroma, tone));
    }

    public double getHue() { return hue; }
    public double getChroma() { return chroma; }
    public double getTone() { return tone; }
    public int toInt() { return argb; }

    private void setInternalState(int argb) {
        this.argb = argb;
        Cam16 cam = Cam16.fromInt(argb);
        this.hue = cam.getHue();
        this.chroma = cam.getChroma();
        this.tone = ColorUtils.lstarFromArgb(argb);
    }

    // The ARGB whose HCT is closest to (hue, chroma, tone): exact tone, exact hue, and
    // the requested chroma reduced to the most the sRGB gamut allows at that tone. Bisect
    // chroma; a midpoint is realizable when the gamut-clipped result keeps roughly that
    // chroma (the 1.0 slack absorbs 8-bit rounding).
    static int toArgb(double hue, double chroma, double tone) {
        if (chroma < 1.0 || Math.round(tone) <= 0.0 || Math.round(tone) >= 100.0) {
            return ColorUtils.argbFromLstar(tone);
        }
        hue = MathUtils.sanitizeDegreesDouble(hue);
        double low = 0.0;
        double high = chroma;
        int answer = ColorUtils.argbFromLstar(tone);
        while (high - low >= 0.4) {
            double mid = low + (high - low) / 2.0;
            Cam16 cam = findCamByJ(hue, mid, tone);
            if (cam != null && Cam16.fromInt(cam.toInt()).getChroma() >= mid - 1.0) {
                answer = cam.toInt();
                low = mid;
            } else {
                high = mid;
            }
        }
        return answer;
    }

    // Bisect CAM16 lightness J so the clipped (in-gamut) color hits the target tone,
    // keeping hue/chroma; returns the best in-gamut CAM16 or null if none qualifies.
    private static Cam16 findCamByJ(double hue, double chroma, double tone) {
        double low = 0.0;
        double high = 100.0;
        double bestdL = 1000.0;
        double bestdE = 1000.0;
        Cam16 bestCam = null;
        while (Math.abs(low - high) > LIGHTNESS_SEARCH_ENDPOINT) {
            double mid = low + (high - low) / 2.0;
            Cam16 camBeforeClip = Cam16.fromJch(mid, chroma, hue);
            int clipped = camBeforeClip.toInt();
            double clippedLstar = ColorUtils.lstarFromArgb(clipped);
            double dL = Math.abs(tone - clippedLstar);
            if (dL < DL_MAX) {
                Cam16 camClipped = Cam16.fromInt(clipped);
                double dE = camClipped.distance(
                    Cam16.fromJch(camClipped.getJ(), camClipped.getChroma(), hue));
                if (dE <= DE_MAX && dE <= bestdE) {
                    bestdL = dL;
                    bestdE = dE;
                    bestCam = camClipped;
                }
            }
            if (bestdL == 0 && bestdE == 0) {
                break;
            }
            if (clippedLstar < tone) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return bestCam;
    }
}
