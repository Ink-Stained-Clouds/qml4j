package io.github.timer_err.qml4j.runtime.color;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemeTest {

    // The TonalSpot scheme for the MD3 baseline seed #6750A4. Values match Google's
    // SchemeTonalSpot (primary is chroma-capped to 36, hence #65558f not the raw seed).
    @Test
    void lightSchemeForBaselineSeed() {
        Map<String, String> light = Scheme.generate(0xFF6750A4, false);
        assertEquals("#65558f", light.get("primary"));
        assertEquals("#ffffff", light.get("onPrimaryColor"));
        assertEquals("#e9ddff", light.get("primaryContainer"));
        assertEquals("#7d5260", light.get("tertiary"));
        assertEquals("#fdf7ff", light.get("background"));
    }

    // MaterialDynamicColors pins background and surface to the same neutral tone; a component
    // that masks a window-coloured hole with `surface` (the MD3 TextField's floating label)
    // depends on it.
    @Test
    void backgroundEqualsSurface() {
        Map<String, String> light = Scheme.generate(0xFF6750A4, false);
        Map<String, String> dark = Scheme.generate(0xFF6750A4, true);
        assertEquals(light.get("surface"), light.get("background"));
        assertEquals(dark.get("surface"), dark.get("background"));
    }

    @Test
    void darkSchemeForBaselineSeed() {
        Map<String, String> dark = Scheme.generate(0xFF6750A4, true);
        assertEquals("#cfbdfd", dark.get("primary"));
        assertEquals("#141218", dark.get("surface"));
        assertEquals("#ffb3aa", dark.get("error"));
        assertEquals("#e6e0e8", dark.get("onSurfaceColor"));
    }

    @Test
    void schemeHasAllRoles() {
        Map<String, String> light = Scheme.generate(0xFF6750A4, false);
        assertEquals(36, light.size());
        for (String hex : light.values()) {
            assertEquals(7, hex.length(), hex);
        }
    }
}
