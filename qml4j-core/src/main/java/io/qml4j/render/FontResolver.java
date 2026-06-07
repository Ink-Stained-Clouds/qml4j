package io.qml4j.render;

import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.util.HashMap;
import java.util.Map;

// Typeface selection and caching: the default Latin face, a CJK fallback, a
// per-codepoint symbol face, and the bundled Material Symbols icon face.
// fontFor() picks the face that actually covers the given text.
final class FontResolver {

    private Typeface defaultTypeface;
    private Typeface cjkTypeface;
    private boolean cjkLookupFailed;
    private Typeface iconTypeface;
    private boolean iconLookupFailed;
    private ResourceLoader resources;

    private final Map<Integer, Typeface> symbolCache = new HashMap<>();

    private static final String[] LATIN_CANDIDATES = {
        null, "sans-serif", "Roboto", "Droid Sans", "Arial"
    };

    private static final String[] CJK_CANDIDATES = {
        "Noto Sans CJK SC", "NotoSansCJK", "Noto Sans CJK",
        "Source Han Sans SC", "Source Han Sans",
        "PingFang SC", "Heiti SC", "Droid Sans Fallback",
        "Microsoft YaHei", "WenQuanYi Micro Hei"
    };

    void setResourceLoader(ResourceLoader loader) {
        this.resources = loader;
    }

    Font fontFor(float size, String text) {
        return fontFor(size, text, false);
    }

    // `bold` synthesizes weight via Skija's emboldening (our bundled faces are single
    // weight, so there is no real bold variant to match).
    Font fontFor(float size, String text, boolean bold) {
        Typeface tf;
        if (isSymbol(text)) tf = symbolTypeface(text.codePointAt(0));
        else if (needsCjk(text)) tf = cjkTypeface();
        else tf = null;
        if (tf == null) tf = defaultTypeface();
        Font f = tf != null ? new Font(tf, size) : new Font().setSize(size);
        return bold ? f.setEmboldened(true) : f;
    }

    Font font(float size) {
        return fontFor(size, null);
    }

    // The bundled Material Symbols font (icon glyphs are ligatures of the names).
    // Loaded once from resources; null if absent (then we fall back to the
    // curated Unicode mapping).
    Typeface iconTypeface() {
        if (iconTypeface != null) return iconTypeface;
        if (iconLookupFailed || resources == null) return null;
        byte[] bytes = resources.load("fonts/MaterialSymbolsOutlined.ttf");
        FontMgr mgr = FontMgr.getDefault();
        if (bytes == null || mgr == null) { iconLookupFailed = true; return null; }
        try {
            iconTypeface = mgr.makeFromData(Data.makeFromBytes(bytes));
            if (iconTypeface == null) iconLookupFailed = true;
            return iconTypeface;
        } catch (Throwable t) {
            iconLookupFailed = true;
            return null;
        }
    }

    private Typeface defaultTypeface() {
        if (defaultTypeface != null) return defaultTypeface;
        FontMgr mgr = FontMgr.getDefault();
        if (mgr != null) {
            for (String name : LATIN_CANDIDATES) {
                Typeface t = mgr.matchFamilyStyle(name, FontStyle.NORMAL);
                if (t != null) { defaultTypeface = t; return t; }
            }
        }
        return null;
    }

    private Typeface cjkTypeface() {
        if (cjkTypeface != null) return cjkTypeface;
        if (cjkLookupFailed) return null;
        FontMgr mgr = FontMgr.getDefault();
        if (mgr == null) { cjkLookupFailed = true; return null; }
        for (String name : CJK_CANDIDATES) {
            Typeface t = mgr.matchFamilyStyle(name, FontStyle.NORMAL);
            if (t != null) { cjkTypeface = t; return t; }
        }
        try {
            Typeface t = mgr.matchFamilyStyleCharacter(
                null, FontStyle.NORMAL, new String[]{"zh-CN", "zh-Hans"}, 0x4E2D);
            if (t != null) { cjkTypeface = t; return t; }
        } catch (Throwable ignored) {}
        cjkLookupFailed = true;
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
        if (defaultTypeface != null) {
            defaultTypeface.close();
            defaultTypeface = null;
        }
        if (cjkTypeface != null) {
            cjkTypeface.close();
            cjkTypeface = null;
        }
    }
}
