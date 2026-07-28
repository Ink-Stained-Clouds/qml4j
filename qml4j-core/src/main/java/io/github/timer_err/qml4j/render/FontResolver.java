package io.github.timer_err.qml4j.render;

import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.util.HashMap;
import java.util.Map;

// Typeface selection and caching. The engine ships no fonts of its own: the host
// injects the UI faces (default / bold / CJK / icon) via the setters below, and
// the system font manager is only a last-resort fallback. fontFor() picks the
// face that actually covers the given text.
final class FontResolver {

    // App-injected faces. The default also covers CJK when no separate CJK face is
    // given (a font like PingFang spans both scripts); bold null → synthesized.
    private Typeface uiDefault;
    private Typeface uiBold;
    private Typeface uiCjk;
    private Typeface uiIcon;

    // Lazily-resolved system fallbacks (only used when the host injects nothing).
    private Typeface systemDefault;
    private Typeface systemCjk;
    private boolean systemCjkFailed;

    private final Map<Integer, Typeface> symbolCache = new HashMap<>();
    // Cache resolved Fonts: building a Skija Font per text per frame (this runs in
    // both the measure pass and the draw) churned native objects and stuttered
    // text-heavy frames. Font/Typeface are CPU objects (no GPU handle), so caching
    // them across frames is safe; callers must NOT close the returned Font.
    private final Map<String, Font> fontCache = new HashMap<>();

    private static final String[] LATIN_CANDIDATES = {
        null, "sans-serif", "Roboto", "Droid Sans", "Arial"
    };

    private static final String[] CJK_CANDIDATES = {
        "Noto Sans CJK SC", "NotoSansCJK", "Noto Sans CJK",
        "Source Han Sans SC", "Source Han Sans",
        "PingFang SC", "Heiti SC", "Droid Sans Fallback",
        "Microsoft YaHei", "WenQuanYi Micro Hei"
    };

    // Inject the UI font (regular + medium) from host-provided bytes; either may be
    // null to leave that face unchanged. The regular face also serves as the CJK
    // face unless a separate one is set via {@link #setCjkTypeface}.
    void setUiTypefaces(byte[] regular, byte[] medium) {
        if (regular != null) {
            Typeface t = makeFace(regular);
            if (t != null) uiDefault = t;
        }
        if (medium != null) {
            Typeface t = makeFace(medium);
            if (t != null) uiBold = t;
        }
        fontCache.clear();
    }

    /** Inject a dedicated CJK face (optional; default font is used otherwise). */
    void setCjkTypeface(byte[] bytes) {
        Typeface t = makeFace(bytes);
        if (t != null) {
            uiCjk = t;
            fontCache.clear();
        }
    }

    /** Inject the icon face (e.g. Material Symbols; glyphs are name ligatures). */
    void setIconTypeface(byte[] bytes) {
        Typeface t = makeFace(bytes);
        if (t != null) uiIcon = t;
    }

    private static Typeface makeFace(byte[] bytes) {
        FontMgr mgr = FontMgr.getDefault();
        if (bytes == null || mgr == null) return null;
        try {
            return mgr.makeFromData(Data.makeFromBytes(bytes));
        } catch (Throwable t) {
            return null;
        }
    }

    Font fontFor(float size, String text) {
        return fontFor(size, text, false);
    }

    // Bold uses the injected medium/bold face when available (real weight matches the
    // design metrics); otherwise synthesize weight via Skija's emboldening.
    // The Font built here is deliberately not closed at the end of this method: it goes into
    // fontCache to be reused across frames (that is the whole point of the cache) and is closed
    // by close(). Wrapping it in try-with-resources would free the native font on first use.
    @SuppressWarnings("resource")
    Font fontFor(float size, String text, boolean bold) {
        Typeface tf = null;
        boolean realBold = false;
        if (isSymbol(text)) tf = symbolTypeface(text.codePointAt(0));
        else if (needsCjk(text)) tf = cjkTypeface();
        else if (bold) { tf = boldTypeface(); realBold = tf != null; }
        if (tf == null) tf = defaultTypeface();
        boolean embolden = bold && !realBold;
        String key = System.identityHashCode(tf) + ":" + size + ":" + embolden;
        Font cached = fontCache.get(key);
        if (cached != null) return cached;
        Font f = configure(tf != null ? new Font(tf, size) : new Font().setSize(size));
        if (embolden) f.setEmboldened(true);
        fontCache.put(key, f);
        return f;
    }

    /**
     * Shared rasterisation setup for every Font the engine builds — text, icons, and
     * the transient fonts used for measurement alike. Measured metrics must match the
     * ones used at paint or centred content drifts, so this must not be bypassed.
     *
     * <p>Subpixel positioning keeps glyphs at fractional offsets, so text
     * scrolls/animates smoothly instead of snapping to the pixel grid frame to frame.
     *
     * <p>Hinting is off because it is the one part of the pipeline that is not
     * portable: FreeType (Linux) really grid-fits outlines and rounds ascent/descent/
     * advances, DirectWrite (Windows) barely does. Centring an icon relies on the
     * glyph's ink sitting where the font's design metrics say it does — {@code
     * TextLayout.centeredBaseline} places the baseline at {@code -(ascent+descent)/2}
     * below the box centre, which is exact for Material Symbols (ink centre 480/960em
     * == -(ascent+descent)/2). Grid-fitting shifts the ink off that predicted centre,
     * and at icon sizes (~10px) the shift is a visible fraction of the glyph, so icons
     * land off-centre on Linux only. NONE + linear metrics gives pure scaled-outline
     * geometry, identical on both backends, and is what subpixel positioning wants
     * anyway — the two settings otherwise fight each other.
     */
    static Font configure(Font f) {
        f.setSubpixel(true);
        f.setHinting(FontHinting.NONE);
        f.setMetricsLinear(true);
        return f;
    }

    @SuppressWarnings("unused")
    Font font(float size) {
        return fontFor(size, null);
    }

    // The host-injected icon face; null falls back to the curated Unicode mapping.
    Typeface iconTypeface() {
        return uiIcon;
    }

    private Typeface defaultTypeface() {
        if (uiDefault != null) return uiDefault;
        if (systemDefault != null) return systemDefault;
        FontMgr mgr = FontMgr.getDefault();
        if (mgr != null) {
            for (String name : LATIN_CANDIDATES) {
                Typeface t = mgr.matchFamilyStyle(name, FontStyle.NORMAL);
                if (t != null) { systemDefault = t; return t; }
            }
        }
        return null;
    }

    private Typeface boldTypeface() {
        return uiBold;
    }

    private Typeface cjkTypeface() {
        if (uiCjk != null) return uiCjk;
        if (uiDefault != null) return uiDefault;
        if (systemCjk != null) return systemCjk;
        if (systemCjkFailed) return null;
        FontMgr mgr = FontMgr.getDefault();
        if (mgr == null) { systemCjkFailed = true; return null; }
        for (String name : CJK_CANDIDATES) {
            Typeface t = mgr.matchFamilyStyle(name, FontStyle.NORMAL);
            if (t != null) { systemCjk = t; return t; }
        }
        try {
            Typeface t = mgr.matchFamilyStyleCharacter(
                null, FontStyle.NORMAL, new String[]{"zh-CN", "zh-Hans"}, 0x4E2D);
            if (t != null) { systemCjk = t; return t; }
        } catch (Throwable ignored) {}
        systemCjkFailed = true;
        return null;
    }

    // A typeface that actually contains the given symbol codepoint. The default
    // Latin face lacks glyphs like the icon symbols, so ask the font manager for
    // any font that covers it (Noto etc.), falling back to the CJK face.
    private Typeface symbolTypeface(int cp) {
        if (symbolCache.containsKey(cp)) return symbolCache.get(cp);
        Typeface t = null;
        FontMgr mgr = FontMgr.getDefault();
        if (mgr != null) {
            try { t = mgr.matchFamilyStyleCharacter(null, FontStyle.NORMAL, null, cp); }
            catch (Throwable ignored) {}
        }
        if (t == null) t = cjkTypeface();
        symbolCache.put(cp, t);
        return t;
    }

    // General-punctuation through misc-symbols/dingbats: where our icon glyphs live.
    private static boolean isSymbol(String s) {
        if (s == null || s.isEmpty()) return false;
        int c = s.codePointAt(0);
        return c >= 0x2000 && c <= 0x2BFF;
    }

    private static boolean needsCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x3000) return true;
        }
        return false;
    }

    void close() {
        // Close the cached Fonts FIRST: each holds a native ref to its Typeface, so closing
        // only the Typefaces below would leave the (large, e.g. CJK) glyph data alive until a
        // GC collects these Font wrappers -- and native memory never triggers a GC, so it
        // accumulates across hot-reloads. Closing them here frees the font data deterministically.
        for (Font f : fontCache.values()) {
            if (f != null) { try { f.close(); } catch (Throwable ignored) {} }
        }
        fontCache.clear();
        for (Typeface t : symbolCache.values()) {
            if (t != null) { try { t.close(); } catch (Throwable ignored) {} }
        }
        symbolCache.clear();
        if (systemDefault != null) { systemDefault.close(); systemDefault = null; }
        if (systemCjk != null) { systemCjk.close(); systemCjk = null; }
        if (uiDefault != null) { uiDefault.close(); uiDefault = null; }
        if (uiBold != null) { uiBold.close(); uiBold = null; }
        if (uiCjk != null) { uiCjk.close(); uiCjk = null; }
        if (uiIcon != null) { uiIcon.close(); uiIcon = null; }
    }
}
