package io.qml4j.runtime.color;

import java.util.LinkedHashMap;
import java.util.Map;

// Generates an MD3 color scheme from a seed color, the TonalSpot way (the upstream's
// SchemeTonalSpot): six tonal palettes derived from the seed's HCT hue, each role pinned
// to a palette tone. Tones are the MD3 baseline assignments for default contrast.
public final class Scheme {

    private Scheme() {}

    // role -> {paletteKey, lightTone, darkTone}; paletteKey: P S T E N V (neutralVariant).
    private static final Object[][] ROLES = {
        {"primary", 'P', 40, 80},
        {"onPrimaryColor", 'P', 100, 20},
        {"primaryContainer", 'P', 90, 30},
        {"onPrimaryContainerColor", 'P', 10, 90},
        {"secondary", 'S', 40, 80},
        {"onSecondaryColor", 'S', 100, 20},
        {"secondaryContainer", 'S', 90, 30},
        {"onSecondaryContainerColor", 'S', 10, 90},
        {"tertiary", 'T', 40, 80},
        {"onTertiaryColor", 'T', 100, 20},
        {"tertiaryContainer", 'T', 90, 30},
        {"onTertiaryContainerColor", 'T', 10, 90},
        {"error", 'E', 40, 80},
        {"onErrorColor", 'E', 100, 20},
        {"errorContainer", 'E', 90, 30},
        {"onErrorContainerColor", 'E', 10, 90},
        {"background", 'N', 99, 10},
        {"onBackgroundColor", 'N', 10, 90},
        {"surface", 'N', 98, 6},
        {"onSurfaceColor", 'N', 10, 90},
        {"surfaceVariant", 'V', 90, 30},
        {"onSurfaceVariantColor", 'V', 30, 80},
        {"outline", 'V', 50, 60},
        {"outlineVariant", 'V', 80, 30},
        {"shadow", 'N', 0, 0},
        {"scrim", 'N', 0, 0},
        {"inverseSurface", 'N', 20, 90},
        {"inverseOnSurface", 'N', 95, 20},
        {"inversePrimary", 'P', 80, 40},
        {"surfaceDim", 'N', 87, 6},
        {"surfaceBright", 'N', 98, 24},
        {"surfaceContainerLowest", 'N', 100, 4},
        {"surfaceContainerLow", 'N', 96, 10},
        {"surfaceContainer", 'N', 94, 12},
        {"surfaceContainerHigh", 'N', 92, 17},
        {"surfaceContainerHighest", 'N', 90, 22},
    };

    public static Map<String, String> generate(int seedArgb, boolean dark) {
        double hue = Hct.fromInt(seedArgb).getHue();
        TonalPalette primary = TonalPalette.fromHueAndChroma(hue, 36.0);
        TonalPalette secondary = TonalPalette.fromHueAndChroma(hue, 16.0);
        TonalPalette tertiary = TonalPalette.fromHueAndChroma(
            MathUtils.sanitizeDegreesDouble(hue + 60.0), 24.0);
        TonalPalette neutral = TonalPalette.fromHueAndChroma(hue, 6.0);
        TonalPalette neutralVariant = TonalPalette.fromHueAndChroma(hue, 8.0);
        TonalPalette error = TonalPalette.fromHueAndChroma(25.0, 84.0);

        Map<String, String> scheme = new LinkedHashMap<>();
        for (Object[] role : ROLES) {
            char key = (Character) role[1];
            TonalPalette palette = key == 'P' ? primary
                : key == 'S' ? secondary
                : key == 'T' ? tertiary
                : key == 'E' ? error
                : key == 'V' ? neutralVariant
                : neutral;
            int tone = (Integer) role[dark ? 3 : 2];
            scheme.put((String) role[0], hex(palette.tone(tone)));
        }
        return scheme;
    }

    private static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }
}
