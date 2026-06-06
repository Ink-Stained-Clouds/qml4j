package io.qml4j.render;

import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Text;

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

    // Material Symbols icon name -> private-use codepoint, drawn by codepoint
    // with the icon typeface (plain drawString). A simple fast path that needs
    // no text shaping.
    private static final Map<String, Integer> ICON_CODEPOINTS = buildIconCodepoints();

    private static Map<String, Integer> buildIconCodepoints() {
        Map<String, Integer> m = new HashMap<>();
        m.put("add", 0xe145); m.put("arrow_back", 0xe5c4); m.put("arrow_downward", 0xe5db);
        m.put("arrow_forward", 0xe5c8); m.put("arrow_upward", 0xe5d8); m.put("check", 0xe5ca);
        m.put("chevron_left", 0xe5cb); m.put("chevron_right", 0xe5cc); m.put("close", 0xe5cd);
        m.put("done", 0xe876); m.put("expand_less", 0xe5ce); m.put("expand_more", 0xe5cf);
        m.put("favorite", 0xe87e); m.put("home", 0xe9b2); m.put("info", 0xe88e);
        m.put("menu", 0xe5d2); m.put("more_horiz", 0xe5d3); m.put("more_vert", 0xe5d4);
        m.put("remove", 0xe15b); m.put("search", 0xe8b6); m.put("settings", 0xe8b8);
        m.put("star", 0xf09a); m.put("warning", 0xf083);
        m.put("edit", 0xf097); m.put("delete", 0xe92e); m.put("share", 0xe80d);
        m.put("person", 0xe7fd);
        return m;
    }

    static boolean isIconFamily(String family) {
        return family != null && (family.contains("Symbols") || family.contains("Material"));
    }

    // For an icon-font Text with the real Material Symbols face: the glyph string
    // (a PUA codepoint) to draw with the icon typeface. "" = unknown name (drawn
    // blank). null = not an icon font / font unavailable -> use the Unicode map.
    String iconGlyph(Text t) {
        if (!isIconFamily(t.font.family.peek())) return null;
        if (fonts.iconTypeface() == null) return null;
        String name = rawText(t);
        if (name == null) return "";
        Integer cp = ICON_CODEPOINTS.get(name.trim());
        return cp == null ? "" : new String(Character.toChars(cp));
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
