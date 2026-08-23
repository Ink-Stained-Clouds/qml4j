package io.github.timer_err.qml4j.render;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.TextLine;
import io.github.timer_err.qml4j.render.items.core.Text;
import io.github.timer_err.qml4j.render.items.core.TextWrap;
import io.github.timer_err.qml4j.render.items.input.TextEdit;
import io.github.timer_err.qml4j.render.items.window.Button;
import io.github.timer_err.qml4j.render.items.window.Control;

import java.util.HashMap;
import java.util.Map;

// Text and control measurement: implicit-size computation, line wrapping/elision
// caching, and font line metrics. Shared by the layout pre-pass (measure) and
// the paint pass.
public final class TextLayout {

    private final FontResolver fonts;
    private final IconResolver icons;
    private final Map<IconMeasureKey, IconMetrics> iconMetrics = new HashMap<>();
    private final IconMeasureKey iconProbe = new IconMeasureKey();

    private static final class IconMeasureKey {
        int sizeBits;
        String glyph;

        IconMeasureKey() { }
        IconMeasureKey(int sizeBits, String glyph) {
            this.sizeBits = sizeBits;
            this.glyph = glyph;
        }
        @Override public int hashCode() { return sizeBits * 31 + glyph.hashCode(); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof IconMeasureKey)) return false;
            IconMeasureKey key = (IconMeasureKey) other;
            return sizeBits == key.sizeBits && glyph.equals(key.glyph);
        }
    }

    private static final class IconMetrics {
        final float width;
        final float height;
        IconMetrics(float width, float height) { this.width = width; this.height = height; }
    }

    TextLayout(FontResolver fonts, IconResolver icons) {
        this.fonts = fonts;
        this.icons = icons;
    }

    public void measureText(Text t) {
        float size = t.effectiveFontSize();
        boolean canMeasureW = !t.width.isBound() && ownsWidth(t);
        boolean canMeasureH = !t.height.isBound() && ownsHeight(t);

        String ig = icons.iconGlyph(t);
        if (ig != null) {
            int sizeBits = Float.floatToIntBits(size);
            iconProbe.sizeBits = sizeBits;
            iconProbe.glyph = ig;
            IconMetrics metrics = iconMetrics.get(iconProbe);
            if (metrics == null) {
                Font font = fonts.iconFont(size);
                float width = 0f;
                if (!ig.isEmpty()) {
                    try (TextLine line = TextLine.make(ig, font)) {
                        width = line.getWidth();
                    }
                }
                metrics = new IconMetrics(width, lineHeight(font));
                iconMetrics.put(new IconMeasureKey(sizeBits, ig), metrics);
            }
            float w = metrics.width;
            float h = metrics.height;
            if (!t.implicitWidth.isBound()) t.implicitWidth.set(w);
            if (!t.implicitHeight.isBound()) t.implicitHeight.set(h);
            if (canMeasureW) { t.width.set(w); t.lastSetWidth = w; }
            if (canMeasureH) { t.height.set(h); t.lastSetHeight = h; }
            return;
        }

        String s = icons.displayText(t);
        String[] lines = splitLines(s);
        String wrapMode = wrapModeString(t.wrapMode.peekInt());
        // When wrapping is on and the width is externally constrained (a layout,
        // anchor, or explicit width set it -- i.e. we don't own it), the natural
        // line count grows; report the wrapped height so containers size to it.
        float wrapW = (wrapMode != null && (t.width.isBound() || !ownsWidth(t)))
            ? t.width.peekFloat() : 0f;
        // Measure with the same weight paint uses, or a bold label wraps to more lines at
        // render than measure reserved height for, spilling past the laid-out box.
        boolean bold = Boolean.TRUE.equals(t.font.bold.peek()) || t.font.weight.peekInt() >= 63;
        // Text shaping (measureTextWidth / wrap) is expensive and measureText runs once per
        // settle pass AND again per node at draw; cache the natural size keyed on the inputs
        // that affect it, so an unchanged label re-measures for free.
        int maxLines = t.maximumLineCount.peekInt();
        float w, h;
        if (s.equals(t.cachedText) && size == t.cachedSize && bold == t.cachedBold
                && wrapW == t.cachedWrapW && maxLines == t.cachedMaxLines) {
            w = t.cachedW;
            h = t.cachedH;
        } else {
            { Font font = fonts.fontFor(size, s, bold);
                float mw = 0f;
                for (String line : lines) {
                    float lw = font.measureTextWidth(line);
                    if (lw > mw) mw = lw;
                }
                int lineCount = lines.length;
                if (wrapW > 0f && mw > wrapW) {
                    lineCount = TextWrap.wrap(s, wrapMode, wrapW,
                        seg -> font.measureTextWidth(seg)).lines.size();
                }
                if (maxLines > 0 && lineCount > maxLines) lineCount = maxLines;
                w = mw;
                h = lineHeight(font) * lineCount;
            }
            t.cachedText = s; t.cachedSize = size; t.cachedBold = bold;
            t.cachedWrapW = wrapW; t.cachedMaxLines = maxLines; t.cachedW = w; t.cachedH = h;
        }
        // Natural content size always feeds implicitWidth/Height (Qt), even
        // when width/height are bound externally (e.g. a tooltip background
        // binds to label.implicitWidth).
        if (!t.implicitWidth.isBound()) t.implicitWidth.set(w);
        if (!t.implicitHeight.isBound()) t.implicitHeight.set(h);
        t.contentWidth.set(w);
        t.contentHeight.set(h);
        if (canMeasureW) {
            t.width.set(w);
            t.lastSetWidth = w;
        }
        if (canMeasureH) {
            t.height.set(h);
            t.lastSetHeight = h;
        }
    }

    void dispose() {
        iconMetrics.clear();
    }

    public void measureButton(Button b) {
        float size = b.fontSize.peekFloat();
        String label = b.text.peek();
        float tw, textH;
        { Font font = fonts.fontFor(size, label == null ? "" : label);
            tw = (label == null || label.isEmpty()) ? 0f : font.measureTextWidth(label);
            textH = lineHeight(font);
        }
        float iw = tw + 2f * horizontalPad(b, 16f);
        float ih = textH + 2f * verticalPad(b, 10f);
        // Controls publish their measured content size as implicitWidth/Height;
        // followImplicitSize then copies it into width/height when unset.
        if (!b.implicitWidth.isBound()) b.implicitWidth.set(iw);
        if (!b.implicitHeight.isBound()) b.implicitHeight.set(ih);
    }

    // Text.wrapMode enum -> TextWrap mode string; null when wrapping is off.
    static String wrapModeString(int mode) {
        switch (mode) {
            case 1: return "WordWrap";
            case 3: return "WrapAnywhere";
            case 4: return "Wrap";
            default: return null; // NoWrap
        }
    }

    private static float horizontalPad(Control c, float fallback) {
        float p = c.padding.peekFloat();
        return p > 0f ? p : fallback;
    }

    private static float verticalPad(Control c, float fallback) {
        float p = c.padding.peekFloat();
        return p > 0f ? p : fallback;
    }

    static String[] splitLines(String s) {
        if (s == null || s.isEmpty()) return new String[]{""};
        return s.split("\n", -1);
    }

    private static boolean ownsWidth(Text t) {
        if (Double.isNaN(t.lastSetWidth)) return t.width.peekDouble() == 0.0;
        return t.width.peekDouble() == t.lastSetWidth;
    }

    private static boolean ownsHeight(Text t) {
        if (Double.isNaN(t.lastSetHeight)) return t.height.peekDouble() == 0.0;
        return t.height.peekDouble() == t.lastSetHeight;
    }

    @SuppressWarnings("unused")
    static String elideToWidth(String line, Font font, float maxWidth) {
        if (maxWidth <= 0 || font.measureTextWidth(line) <= maxWidth) return line;
        String ell = "…";
        float ellW = font.measureTextWidth(ell);
        int end = line.length();
        while (end > 0 && font.measureTextWidth(line.substring(0, end)) + ellW > maxWidth) end--;
        return line.substring(0, end) + ell;
    }

    static float glyphTopOffset(Font font) {     // <= 0, relative to baseline
        return font.getMetrics().getAscent();
    }

    static float glyphExtent(Font font) {        // ascent..descent height
        FontMetrics m = font.getMetrics();
        return m.getDescent() - m.getAscent();
    }

    static float lineHeight(Font font) {
        return font.getMetrics().getHeight();
    }

    static float baselineInLine(Font font) {     // line-top -> baseline distance
        return -font.getMetrics().getAscent();
    }

    // Baseline that vertically centres a single line of glyphs in a box of height boxH.
    static float centeredBaseline(Font font, float boxH) {
        FontMetrics m = font.getMetrics();
        return boxH / 2f - (m.getAscent() + m.getDescent()) / 2f;
    }

    TextWrap.Result wrapFor(TextEdit te, String s, float width, float size, Font font) {
        String mode = te.wrapMode.peek();
        if (te.cachedLines != null && s.equals(te.cachedText)
            && (mode == null ? te.cachedWrap == null : mode.equals(te.cachedWrap))
            && te.cachedWidth == width && te.cachedFontSize == size) {
            return new TextWrap.Result(te.cachedLines, te.cachedStarts);
        }
        TextWrap.Result r = TextWrap.wrap(s, mode, width,
            seg -> font.measureTextWidth(seg));
        te.cachedLines = r.lines;
        te.cachedStarts = r.starts;
        te.cachedText = s;
        te.cachedWrap = mode;
        te.cachedWidth = width;
        te.cachedFontSize = size;
        return r;
    }

    float topOffset(String align, float h, float total) {
        if ("AlignVCenter".equals(align)) return Math.max(0, (h - total) / 2f);
        if ("AlignBottom".equals(align)) return Math.max(0, h - total);
        return 0;
    }
}
