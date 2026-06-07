package io.qml4j.runtime.color;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

// Native singleton matching the upstream MD3 StyleManager: it turns a seed color into
// the light and dark MD3 schemes (role -> hex maps) and exposes the active one. The
// upstream Theme.qml reads currentScheme/lightScheme/darkScheme off it. Writing
// isDarkTheme or seedColor regenerates the schemes, and the reactive currentScheme
// re-pushes so Theme.color (and everything bound through it) repaints.
public final class StyleManager extends QObject {

    private static StyleManager instance;

    public static Object __instance() {
        if (instance == null) {
            instance = new StyleManager();
        }
        return instance;
    }

    public final Property<Object> isDarkTheme = new Property<>(Boolean.FALSE);
    public final Property<Object> seedColor = new Property<>("#6750A4");
    public final Property<Object> currentScheme = new Property<>();
    public final Property<Object> lightScheme = new Property<>();
    public final Property<Object> darkScheme = new Property<>();
    public final Property<Object> hctHue = new Property<>(0.0);
    public final Property<Object> hctChroma = new Property<>(0.0);
    public final Property<Object> hctTone = new Property<>(0.0);

    private StyleManager() {
        isDarkTheme.setInterceptor((p, v) -> {
            p.setBypassInterceptor(v);
            updateSchemes();
        });
        seedColor.setInterceptor((p, v) -> {
            p.setBypassInterceptor(v);
            updateSchemes();
        });
        updateSchemes();
    }

    public void setSeedColorHct(double hue, double chroma, double tone) {
        seedColor.set(hex(Hct.from(hue, chroma, tone).toInt()));
    }

    // Image-derived seeds (quantize + score) are unported; ignore so callers don't fail.
    public void setSourceImage(Object fileUrl) {
    }

    private void updateSchemes() {
        int argb = parseSeed(seedColor.peek());
        lightScheme.set(Scheme.generate(argb, false));
        darkScheme.set(Scheme.generate(argb, true));
        boolean dark = Boolean.TRUE.equals(isDarkTheme.peek());
        currentScheme.set(dark ? darkScheme.peek() : lightScheme.peek());
        Hct hct = Hct.fromInt(argb);
        hctHue.set(hct.getHue());
        hctChroma.set(hct.getChroma());
        hctTone.set(hct.getTone());
    }

    private static int parseSeed(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 8) {
            return (int) Long.parseLong(s, 16);
        }
        if (s.length() == 6) {
            return 0xFF000000 | Integer.parseInt(s, 16);
        }
        return 0xFF6750A4;
    }

    private static String hex(int argb) {
        return String.format("#%06x", argb & 0xFFFFFF);
    }
}
