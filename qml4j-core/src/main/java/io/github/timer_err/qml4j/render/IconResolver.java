package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Text;

import java.util.HashMap;
import java.util.Map;

// Material Symbols icon-name resolution. With the real icon font loaded, a name
// maps to a private-use codepoint; otherwise a curated subset maps to standard
// Unicode glyphs the default face can draw.
final class IconResolver {

    private final FontResolver fonts;

    IconResolver(FontResolver fonts) {
        this.fonts = fonts;
    }

    // Material Symbols are accessed by ligature name (e.g. "check"). We don't
    // bundle the ligature shaper; instead a curated subset of names maps to
    // standard Unicode glyphs the default face can draw. Unmapped names render
    // empty rather than as overflowing literal words.
    private static final Map<String, String> ICON_GLYPHS = buildIconGlyphs();

    private static Map<String, String> buildIconGlyphs() {
        Map<String, String> m = new HashMap<>();
        m.put("check", "✓");
        m.put("done", "✓");
        m.put("close", "✕");
        m.put("remove", "−");
        m.put("add", "＋");
        m.put("menu", "☰");
        m.put("more_vert", "⋮");
        m.put("more_horiz", "⋯");
        m.put("search", "⚲");
        m.put("arrow_back", "←");
        m.put("arrow_forward", "→");
        m.put("arrow_upward", "↑");
        m.put("arrow_downward", "↓");
        m.put("chevron_left", "‹");
        m.put("chevron_right", "›");
        m.put("expand_more", "˅");
        m.put("expand_less", "˄");
        m.put("star", "★");
        m.put("favorite", "♥");
        m.put("settings", "⚙");
        m.put("home", "⌂");
        m.put("info", "ⓘ");
        m.put("warning", "⚠");
        return m;
    }

    static boolean isIconFamily(String family) {
        return family != null && (family.contains("Symbols") || family.contains("Material"));
    }

    // For an icon-font Text with the real Material Symbols face: the ligature name to
    // shape with the icon typeface (the font's GSUB turns "widgets" into its glyph), so
    // any Material Symbols name renders without a curated codepoint table. "" when there
    // is no name; null = not an icon font / font unavailable -> use the Unicode map.
    // fonts.iconTypeface() returns a cached Typeface owned by FontResolver -- not closed here.
    @SuppressWarnings("resource")
    String iconGlyph(Text t) {
        if (!isIconFamily(t.font.family.peek())) return null;
        if (fonts.iconTypeface() == null) return null;
        String name = rawText(t);
        return name == null ? "" : name.trim();
    }

    String displayText(Text t) {
        String s = rawText(t);
        if (s == null) return "";
        if (isIconFamily(t.font.family.peek())) {
            return ICON_GLYPHS.getOrDefault(s.trim(), "");
        }
        return s;
    }

    // QML allows binding a number (or any value) to Text.text; it stringifies. Read
    // the raw value (avoiding the Property<String> checkcast) and format numbers the
    // JS/QML way -- integral doubles without a trailing ".0".
    // QML is dynamically typed: a Property<String> can actually hold a boxed number bound from
    // QML, so the Double/Float checks are reachable despite the declared String generic.
    @SuppressWarnings("ConstantValue")
    private static String rawText(Text t) {
        Object raw = ((Property<?>) t.text).peek();
        if (raw == null) return null;
        if (raw instanceof Double || raw instanceof Float) {
            double d = ((Number) raw).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) return Long.toString((long) d);
        }
        return raw.toString();
    }
}
