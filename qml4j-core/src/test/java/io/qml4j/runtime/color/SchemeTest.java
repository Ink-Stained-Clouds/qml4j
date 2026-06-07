package io.qml4j.runtime.color;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemeTest {

    // The TonalSpot scheme for the MD3 baseline seed #6750A4. Values match Google's
    // SchemeTonalSpot (primary is chroma-capped to 36, hence #65558F not the raw seed).
    @Test
    void lightSchemeForBaselineSeed() {
        Map<String, String> light = Scheme.generate(0xFF6750A4, false);
        assertEquals("#65558F", light.get("primary"));
        assertEquals("#FFFFFF", light.get("onPrimaryColor"));
        assertEquals("#E9DDFF", light.get("primaryContainer"));
        assertEquals("#7D5260", light.get("tertiary"));
        assertEquals("#FFFBFE", light.get("background"));
    }

    @Test
    void darkSchemeForBaselineSeed() {
        Map<String, String> dark = Scheme.generate(0xFF6750A4, true);
        assertEquals("#CFBDFD", dark.get("primary"));
        assertEquals("#141218", dark.get("surface"));
        assertEquals("#FFB3AA", dark.get("error"));
        assertEquals("#E6E0E8", dark.get("onSurfaceColor"));
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
