package io.github.timer_err.qml4j.runtime.color;

// Ported from Google's material-color-utilities (Apache 2.0). A tonal palette: a fixed
// hue and chroma, addressable by tone (0 black .. 100 white).
final class TonalPalette {

    private final double hue;
    private final double chroma;

    private TonalPalette(double hue, double chroma) {
        this.hue = hue;
        this.chroma = chroma;
    }

    static TonalPalette fromHueAndChroma(double hue, double chroma) {
        return new TonalPalette(hue, chroma);
    }

    int tone(int tone) {
        return Hct.from(hue, chroma, tone).toInt();
    }
}
