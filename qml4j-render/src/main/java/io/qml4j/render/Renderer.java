package io.qml4j.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.Rect;

public final class Renderer {

    private Paint paint;
    private Typeface defaultTypeface;

    private Paint paint() {
        if (paint == null) paint = new Paint();
        return paint;
    }

    private Typeface defaultTypeface() {
        if (defaultTypeface != null) return defaultTypeface;
        FontMgr mgr = FontMgr.getDefault();
        if (mgr != null) {
            String[] candidates = {null, "sans-serif", "Roboto", "Droid Sans", "Arial"};
            for (String name : candidates) {
                Typeface t = mgr.matchFamilyStyle(name, FontStyle.NORMAL);
                if (t != null) { defaultTypeface = t; return t; }
            }
        }
        return null;
    }

    public void render(Canvas canvas, Item root) {
        if (root == null) return;
        draw(canvas, root);
    }

    private void draw(Canvas canvas, Item node) {
        if (!node.visible.peek()) return;
        if (node instanceof Column) {
            ((Column) node).layout();
        }
        float x = node.x.peek().floatValue();
        float y = node.y.peek().floatValue();
        float w = node.width.peek().floatValue();
        float h = node.height.peek().floatValue();
        float alpha = node.opacity.peek().floatValue();
        if (alpha <= 0f) return;

        int savedCount = canvas.save();
        try {
            canvas.translate(x, y);
            paintNode(canvas, node, w, h, alpha);
            for (Item child : node.children) {
                draw(canvas, child);
            }
        } finally {
            canvas.restoreToCount(savedCount);
        }
    }

    private void paintNode(Canvas canvas, Item node, float w, float h, float alpha) {
        if (node instanceof Rectangle) {
            Rectangle r = (Rectangle) node;
            int color = applyAlpha(parseColor(r.color.peek()), alpha);
            paint().setColor(color);
            canvas.drawRect(Rect.makeXYWH(0, 0, w, h), paint);
        } else if (node instanceof Text) {
            Text t = (Text) node;
            int color = applyAlpha(parseColor(t.color.peek()), alpha);
            paint().setColor(color);
            float size = t.fontSize.peek().floatValue();
            try (Font font = font(size)) {
                String s = t.text.peek();
                if (s != null && !s.isEmpty()) {
                    canvas.drawString(s, 0, size, font, paint);
                }
            }
        }
    }

    private Font font(float size) {
        Typeface tf = defaultTypeface();
        if (tf != null) return new Font(tf, size);
        return new Font().setSize(size);
    }

    public void dispose() {
        if (paint != null) {
            paint.close();
            paint = null;
        }
        if (defaultTypeface != null) {
            defaultTypeface.close();
            defaultTypeface = null;
        }
    }

    static int parseColor(String s) {
        if (s == null) return 0xFF000000;
        s = s.trim();
        if (s.isEmpty() || s.charAt(0) != '#') return 0xFF000000;
        String hex = s.substring(1);
        long v;
        try { v = Long.parseLong(hex, 16); }
        catch (NumberFormatException e) { return 0xFF000000; }
        switch (hex.length()) {
            case 3: {
                int r = (int) ((v >> 8) & 0xF);
                int g = (int) ((v >> 4) & 0xF);
                int b = (int) (v & 0xF);
                return 0xFF000000 | (r * 0x11 << 16) | (g * 0x11 << 8) | (b * 0x11);
            }
            case 6:
                return 0xFF000000 | (int) (v & 0xFFFFFFL);
            case 8:
                return (int) v;
            default:
                return 0xFF000000;
        }
    }

    private static int applyAlpha(int color, float alpha) {
        if (alpha >= 1f) return color;
        if (alpha <= 0f) return color & 0x00FFFFFF;
        int a = (color >>> 24) & 0xFF;
        int na = Math.round(a * alpha);
        return (na << 24) | (color & 0x00FFFFFF);
    }
}
