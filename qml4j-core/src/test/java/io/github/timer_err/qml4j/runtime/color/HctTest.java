package io.github.timer_err.qml4j.runtime.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HctTest {

    private static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    @Test
    void fromIntMatchesKnownSeed() {
        // Chroma and tone of the MD3 baseline primary #6750A4. Hue is an impl detail of
        // CAM16 (the primaries below pin the model against textbook values).
        Hct hct = Hct.fromInt(0xFF6750A4);
        assertEquals(48.25, hct.getChroma(), 1.0, "chroma");
        assertEquals(40.0, hct.getTone(), 0.5, "tone");
    }

    @Test
    void primaryHuesMatchTextbookCam16() {
        assertEquals(27.4, Hct.fromInt(0xFFFF0000).getHue(), 1.0, "red");
        assertEquals(142.1, Hct.fromInt(0xFF00FF00).getHue(), 1.0, "green");
        assertEquals(282.8, Hct.fromInt(0xFF0000FF).getHue(), 1.0, "blue");
    }

    @Test
    void roundTripsSeed() {
        Hct hct = Hct.fromInt(0xFF6750A4);
        int back = Hct.from(hct.getHue(), hct.getChroma(), hct.getTone()).toInt();
        // Within a couple LSBs of the original.
        assertEquals(0x67, (back >> 16) & 255, 4, "r");
        assertEquals(0x50, (back >> 8) & 255, 4, "g");
        assertEquals(0xA4, back & 255, 4, "b");
    }

    @Test
    void toneExtremesAreGrayscale() {
        assertEquals("#FFFFFF", hex(Hct.from(264.0, 48.0, 100.0).toInt()));
        assertEquals("#000000", hex(Hct.from(264.0, 48.0, 0.0).toInt()));
    }

    @Test
    void primaryContainerLightCloseToBaseline() {
        // MD3 baseline primaryContainer (light) is #EADDFF, primary palette tone 90.
        Hct seed = Hct.fromInt(0xFF6750A4);
        int t90 = Hct.from(seed.getHue(), Math.max(48.0, seed.getChroma()), 90.0).toInt();
        assertEquals(0xEA, (t90 >> 16) & 255, 6, "r");
        assertEquals(0xDD, (t90 >> 8) & 255, 6, "g");
        assertEquals(0xFF, t90 & 255, 6, "b");
    }
}
