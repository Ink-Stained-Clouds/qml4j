package io.qml4j.render;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.QObject;
import io.qml4j.render.items.Column;
import io.qml4j.render.items.Component;
import io.qml4j.render.items.Flickable;
import io.qml4j.render.items.Gradient;
import io.qml4j.render.items.GradientStop;
import io.qml4j.render.items.Image;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Loader;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.Rectangle;
import io.qml4j.render.items.Row;
import io.qml4j.render.items.Text;
import io.qml4j.render.items.TextInput;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Renderer {

    private Paint paint;
    private Typeface defaultTypeface;
    private ResourceLoader resources;
    private ComponentFactory factory;

    public void setResourceLoader(ResourceLoader loader) {
        this.resources = loader;
    }

    public void setComponentFactory(ComponentFactory factory) {
        this.factory = factory;
    }

    private Paint paint() {
        if (paint == null) paint = new Paint();
        return paint;
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

    private Typeface cjkTypeface;
    private boolean cjkLookupFailed;

    private static final String[] LATIN_CANDIDATES = {
        null, "sans-serif", "Roboto", "Droid Sans", "Arial"
    };

    private static final String[] CJK_CANDIDATES = {
        "Noto Sans CJK SC", "NotoSansCJK", "Noto Sans CJK",
        "Source Han Sans SC", "Source Han Sans",
        "PingFang SC", "Heiti SC", "Droid Sans Fallback",
        "Microsoft YaHei", "WenQuanYi Micro Hei"
    };

    private static boolean needsCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x3000) return true;
        }
        return false;
    }

    public void render(Canvas canvas, Item root) {
        if (root == null) return;
        draw(canvas, root, 1f);
    }

    private void draw(Canvas canvas, Item node, float inheritedAlpha) {
        if (!node.visible.peek()) return;
        if (node instanceof Text) measureText((Text) node);
        applyAnchors(node);
        if (node instanceof Loader) {
            resolveLoader((Loader) node);
        }
        if (node instanceof Column) {
            ((Column) node).layout();
        }
        if (node instanceof Row) {
            ((Row) node).layout();
        }
        float x = node.x.peek().floatValue();
        float y = node.y.peek().floatValue();
        float w = node.width.peek().floatValue();
        float h = node.height.peek().floatValue();
        float alpha = inheritedAlpha * node.opacity.peek().floatValue();
        if (alpha <= 0f) return;
        float rot = node.rotation.peek().floatValue();
        float sc = node.scale.peek().floatValue();
        boolean clip = Boolean.TRUE.equals(node.clip.peek());

        int savedCount = canvas.save();
        try {
            canvas.translate(x, y);
            applyTransform(canvas, w, h, rot, sc);
            if (clip) canvas.clipRect(Rect.makeXYWH(0, 0, w, h));
            paintNode(canvas, node, w, h, alpha);
            if (node instanceof Flickable) {
                Flickable f = (Flickable) node;
                canvas.clipRect(Rect.makeXYWH(0, 0, w, h));
                canvas.translate(-f.contentX.peek().floatValue(), -f.contentY.peek().floatValue());
            }
            for (Item child : zOrdered(node.children)) {
                draw(canvas, child, alpha);
            }
        } finally {
            canvas.restoreToCount(savedCount);
        }
    }

    private static void applyTransform(Canvas canvas, float w, float h, float rot, float sc) {
        if (rot == 0f && sc == 1f) return;
        float cx = w / 2f;
        float cy = h / 2f;
        canvas.translate(cx, cy);
        if (rot != 0f) canvas.rotate(rot);
        if (sc != 1f) canvas.scale(sc, sc);
        canvas.translate(-cx, -cy);
    }

    static List<Item> zOrdered(List<Item> children) {
        int n = children.size();
        if (n < 2) return children;
        boolean anyZ = false;
        for (int i = 0; i < n; i++) {
            if (children.get(i).z.peek().floatValue() != 0f) { anyZ = true; break; }
        }
        if (!anyZ) return children;
        List<Item> copy = new ArrayList<>(children);
        copy.sort(Comparator.comparingDouble(c -> c.z.peek().doubleValue()));
        return copy;
    }

    private void measureText(Text t) {
        float size = t.fontSize.peek().floatValue();
        String s = t.text.peek();
        if (s == null) s = "";
        boolean canMeasureW = !t.width.isBound() && ownsWidth(t);
        boolean canMeasureH = !t.height.isBound() && ownsHeight(t);
        if (!canMeasureW && !canMeasureH) return;
        if (s.equals(t.lastMeasuredText) && size == t.lastMeasuredSize) return;
        try (Font font = fontFor(size, s)) {
            if (canMeasureW) {
                float w = font.measureTextWidth(s);
                t.width.set(w);
                t.lastSetWidth = w;
            }
            if (canMeasureH) {
                t.height.set(size);
                t.lastSetHeight = size;
            }
        }
        t.lastMeasuredText = s;
        t.lastMeasuredSize = size;
    }

    private static boolean ownsWidth(Text t) {
        if (Double.isNaN(t.lastSetWidth)) return t.width.peek().doubleValue() == 0.0;
        return t.width.peek().doubleValue() == t.lastSetWidth;
    }

    private static boolean ownsHeight(Text t) {
        if (Double.isNaN(t.lastSetHeight)) return t.height.peek().doubleValue() == 0.0;
        return t.height.peek().doubleValue() == t.lastSetHeight;
    }

    static void applyAnchors(Item node) {
        Anchors a = node.anchors;
        float baseM = a.margins.peek().floatValue();
        float lm = marginOr(a.leftMargin.peek(), baseM);
        float rm = marginOr(a.rightMargin.peek(), baseM);
        float tm = marginOr(a.topMargin.peek(), baseM);
        float bm = marginOr(a.bottomMargin.peek(), baseM);

        Item fill = a.fill.peek();
        if (fill != null) {
            node.x.set(lm);
            node.y.set(tm);
            node.width.set(fill.width.peek().floatValue() - lm - rm);
            node.height.set(fill.height.peek().floatValue() - tm - bm);
            return;
        }
        Item ci = a.centerIn.peek();
        if (ci != null) {
            float w = node.width.peek().floatValue();
            float h = node.height.peek().floatValue();
            node.x.set((ci.width.peek().floatValue() - w) / 2f);
            node.y.set((ci.height.peek().floatValue() - h) / 2f);
            return;
        }
        applyHorizontalAnchors(node, lm, rm, a);
        applyVerticalAnchors(node, tm, bm, a);
    }

    private static void applyHorizontalAnchors(Item node, float lm, float rm, Anchors a) {
        AnchorLine left = a.left.peek();
        AnchorLine right = a.right.peek();
        AnchorLine hcenter = a.horizontalCenter.peek();
        if (left != null && right != null) {
            float l = resolveX(left, node) + lm;
            float r = resolveX(right, node) - rm;
            node.x.set(l);
            node.width.set(r - l);
        } else if (left != null) {
            node.x.set(resolveX(left, node) + lm);
        } else if (right != null) {
            float w = node.width.peek().floatValue();
            node.x.set(resolveX(right, node) - rm - w);
        } else if (hcenter != null) {
            float w = node.width.peek().floatValue();
            float off = a.horizontalCenterOffset.peek().floatValue();
            node.x.set(resolveX(hcenter, node) - w / 2f + off);
        }
    }

    private static void applyVerticalAnchors(Item node, float tm, float bm, Anchors a) {
        AnchorLine top = a.top.peek();
        AnchorLine bottom = a.bottom.peek();
        AnchorLine vcenter = a.verticalCenter.peek();
        if (top != null && bottom != null) {
            float t = resolveY(top, node) + tm;
            float b = resolveY(bottom, node) - bm;
            node.y.set(t);
            node.height.set(b - t);
        } else if (top != null) {
            node.y.set(resolveY(top, node) + tm);
        } else if (bottom != null) {
            float h = node.height.peek().floatValue();
            node.y.set(resolveY(bottom, node) - bm - h);
        } else if (vcenter != null) {
            float h = node.height.peek().floatValue();
            float off = a.verticalCenterOffset.peek().floatValue();
            node.y.set(resolveY(vcenter, node) - h / 2f + off);
        }
    }

    private static float resolveX(AnchorLine line, Item node) {
        Item src = line.source;
        boolean srcIsParent = src == node.parent.peek();
        float base = srcIsParent ? 0f : src.x.peek().floatValue();
        float w = src.width.peek().floatValue();
        switch (line.edge) {
            case LEFT: return base;
            case RIGHT: return base + w;
            case HORIZONTAL_CENTER: return base + w / 2f;
            default: throw new IllegalStateException("not a horizontal edge: " + line.edge);
        }
    }

    private static float resolveY(AnchorLine line, Item node) {
        Item src = line.source;
        boolean srcIsParent = src == node.parent.peek();
        float base = srcIsParent ? 0f : src.y.peek().floatValue();
        float h = src.height.peek().floatValue();
        switch (line.edge) {
            case TOP: return base;
            case BOTTOM: return base + h;
            case VERTICAL_CENTER: return base + h / 2f;
            default: throw new IllegalStateException("not a vertical edge: " + line.edge);
        }
    }

    private static float marginOr(Number margin, float fallback) {
        if (margin == null) return fallback;
        double d = margin.doubleValue();
        if (Double.isNaN(d)) return fallback;
        return (float) d;
    }

    private void paintNode(Canvas canvas, Item node, float w, float h, float alpha) {
        if (node instanceof Rectangle) {
            paintRectangle(canvas, (Rectangle) node, w, h, alpha);
        } else if (node instanceof Image) {
            paintImage(canvas, (Image) node, w, h, alpha);
        } else if (node instanceof TextInput) {
            paintTextInput(canvas, (TextInput) node, w, h, alpha);
        } else if (node instanceof Text) {
            Text t = (Text) node;
            int color = applyAlpha(parseColor(t.color.peek()), alpha);
            paint().setColor(color);
            float size = t.fontSize.peek().floatValue();
            String s = t.text.peek();
            try (Font font = fontFor(size, s)) {
                if (s != null && !s.isEmpty()) {
                    canvas.drawString(s, 0, size, font, paint);
                }
            }
        }
    }

    private void paintRectangle(Canvas canvas, Rectangle r, float w, float h, float alpha) {
        float radius = Math.max(0f, r.radius.peek().floatValue());
        float borderWidth = Math.max(0f, r.border.width.peek().floatValue());
        Gradient g = r.gradient.peek();
        Shader shader = (g != null) ? buildLinearGradient(g, w, h) : null;
        Paint p = paint();
        p.setMode(PaintMode.FILL);
        p.setShader(shader);
        if (shader == null) {
            p.setColor(applyAlpha(parseColor(r.color.peek()), alpha));
        } else {
            p.setColor(applyAlpha(0xFFFFFFFF, alpha));
        }
        if (radius > 0f) {
            canvas.drawRRect(RRect.makeXYWH(0, 0, w, h, radius), p);
        } else {
            canvas.drawRect(Rect.makeXYWH(0, 0, w, h), p);
        }
        p.setShader(null);
        if (shader != null) shader.close();
        if (borderWidth > 0f) {
            p.setMode(PaintMode.STROKE);
            p.setStrokeWidth(borderWidth);
            p.setColor(applyAlpha(parseColor(r.border.color.peek()), alpha));
            float inset = borderWidth / 2f;
            float bw = Math.max(0f, w - borderWidth);
            float bh = Math.max(0f, h - borderWidth);
            if (radius > 0f) {
                float br = Math.max(0f, radius - inset);
                canvas.drawRRect(RRect.makeXYWH(inset, inset, bw, bh, br), p);
            } else {
                canvas.drawRect(Rect.makeXYWH(inset, inset, bw, bh), p);
            }
            p.setMode(PaintMode.FILL);
        }
    }

    private static Shader buildLinearGradient(Gradient g, float w, float h) {
        List<GradientStop> stops = g.stops;
        int n = stops.size();
        if (n == 0) return null;
        int[] colors = new int[n];
        float[] positions = new float[n];
        for (int i = 0; i < n; i++) {
            GradientStop s = stops.get(i);
            colors[i] = parseColor(s.color.peek());
            positions[i] = s.position.peek().floatValue();
        }
        return Shader.makeLinearGradient(0, 0, 0, h, colors, positions);
    }

    private void paintTextInput(Canvas canvas, TextInput ti, float w, float h, float alpha) {
        String s = ti.text.peek();
        if (s == null) s = "";
        float size = ti.fontSize.peek().floatValue();
        try (Font font = fontFor(size, s)) {
            float baseline = size;
            float glyphTop = baseline + size * GLYPH_ASCENT_RATIO;
            float glyphHeight = size * (GLYPH_DESCENT_RATIO - GLYPH_ASCENT_RATIO);
            paintSelectionRect(canvas, ti, s, font, glyphTop, glyphHeight, alpha);
            if (!s.isEmpty()) {
                paint().setColor(applyAlpha(parseColor(ti.color.peek()), alpha));
                canvas.drawString(s, 0, baseline, font, paint);
            }
            if (Boolean.TRUE.equals(ti.activeFocus.peek()) && caretBlinkOn()) {
                int pos = Math.max(0, Math.min(ti.cursorPosition.peek().intValue(), s.length()));
                float cx = font.measureTextWidth(s.substring(0, pos));
                Paint p = paint();
                p.setMode(PaintMode.FILL);
                p.setColor(applyAlpha(parseColor(ti.cursorColor.peek()), alpha));
                float cw = Math.max(1f, size / 16f);
                canvas.drawRect(Rect.makeXYWH(cx, glyphTop, cw, glyphHeight), p);
            }
        }
    }

    private void paintSelectionRect(Canvas canvas, TextInput ti, String s, Font font,
                                    float glyphTop, float glyphHeight, float alpha) {
        int len = s.length();
        int selS = Math.max(0, Math.min(ti.selectionStart.peek().intValue(), len));
        int selE = Math.max(selS, Math.min(ti.selectionEnd.peek().intValue(), len));
        if (selE <= selS) return;
        float x0 = font.measureTextWidth(s.substring(0, selS));
        float x1 = font.measureTextWidth(s.substring(0, selE));
        Paint p = paint();
        p.setMode(PaintMode.FILL);
        p.setColor(applyAlpha(parseColor(ti.selectionColor.peek()), alpha));
        canvas.drawRect(Rect.makeXYWH(x0, glyphTop, x1 - x0, glyphHeight), p);
    }

    private static boolean caretBlinkOn() {
        return (System.currentTimeMillis() / CARET_BLINK_MS) % 2 == 0;
    }

    private static final long CARET_BLINK_MS = 500;

    private static final float GLYPH_ASCENT_RATIO = -0.78f;
    private static final float GLYPH_DESCENT_RATIO = 0.22f;

    public int caretIndexFor(TextInput ti, float localX) {
        String s = ti.text.peek();
        if (s == null || s.isEmpty() || localX <= 0) return 0;
        float size = ti.fontSize.peek().floatValue();
        try (Font font = fontFor(size, s)) {
            float prev = 0f;
            int n = s.length();
            for (int i = 1; i <= n; i++) {
                float w = font.measureTextWidth(s.substring(0, i));
                if (w >= localX) {
                    float mid = (prev + w) / 2f;
                    return localX < mid ? i - 1 : i;
                }
                prev = w;
            }
            return n;
        } catch (Throwable ignored) {
            return s.length();
        }
    }

    private void paintImage(Canvas canvas, Image node, float w, float h, float alpha) {
        String src = node.source.peek();
        if (src == null || src.isEmpty()) return;
        if (!src.equals(node.loadedSource)) {
            if (node.skiaImage != null) {
                node.skiaImage.close();
                node.skiaImage = null;
            }
            node.loadedSource = src;
            node.intrinsicWidth = 0;
            node.intrinsicHeight = 0;
            if (resources != null) {
                byte[] bytes = resources.load(src);
                if (bytes != null) {
                    int[] dim = peekImageDimensions(bytes);
                    node.intrinsicWidth = dim[0];
                    node.intrinsicHeight = dim[1];
                    try {
                        node.skiaImage = io.github.humbleui.skija.Image.makeFromEncoded(bytes);
                    } catch (Throwable t) {
                        node.skiaImage = null;
                    }
                }
            }
        }
        if (node.skiaImage == null) return;
        int iw = node.intrinsicWidth;
        int ih = node.intrinsicHeight;
        if (iw <= 0 || ih <= 0) return;
        if (w <= 0) w = iw;
        if (h <= 0) h = ih;
        Rect src2 = Rect.makeXYWH(0, 0, iw, ih);
        Rect dst = Rect.makeXYWH(0, 0, w, h);
        canvas.drawImageRect(node.skiaImage, src2, dst);
    }

    private static int[] peekImageDimensions(byte[] bytes) {
        if (bytes.length >= 24
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            int w = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                    | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
            int h = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                    | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
            return new int[]{w, h};
        }
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            int i = 2;
            while (i + 9 < bytes.length) {
                if ((bytes[i] & 0xFF) != 0xFF) break;
                int marker = bytes[i + 1] & 0xFF;
                i += 2;
                if (marker == 0xD8 || marker == 0xD9) continue;
                int segLen = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
                if ((marker >= 0xC0 && marker <= 0xC3)
                        || (marker >= 0xC5 && marker <= 0xC7)
                        || (marker >= 0xC9 && marker <= 0xCB)
                        || (marker >= 0xCD && marker <= 0xCF)) {
                    int h = ((bytes[i + 3] & 0xFF) << 8) | (bytes[i + 4] & 0xFF);
                    int w = ((bytes[i + 5] & 0xFF) << 8) | (bytes[i + 6] & 0xFF);
                    return new int[]{w, h};
                }
                i += segLen;
            }
        }
        return new int[]{0, 0};
    }

    void resolveLoader(Loader node) {
        Component sc = node.sourceComponent.peek();
        if (sc != null) {
            resolveLoaderComponent(node, sc);
            return;
        }
        if (node.loadedComponent != null) {
            clearLoadedItem(node);
            node.loadedComponent = null;
        }
        resolveLoaderSource(node);
    }

    private void resolveLoaderSource(Loader node) {
        String src = node.source.peek();
        if (src == null || src.isEmpty()) {
            if (node.loadedItem != null) {
                clearLoadedItem(node);
                node.loadedSource = null;
            }
            return;
        }
        if (src.equals(node.loadedSource)) return;
        if (factory == null || resources == null) return;
        byte[] bytes = resources.load(src);
        if (bytes == null) return;
        String qml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        Item child;
        try {
            child = factory.create(qml);
        } catch (Throwable t) {
            return;
        }
        attachLoadedItem(node, child);
        node.loadedSource = src;
    }

    private void resolveLoaderComponent(Loader node, Component sc) {
        if (sc == node.loadedComponent && node.loadedItem != null) return;
        DelegateFactory df = sc.factory();
        if (df == null) return;
        QObject created = df.create(0, null);
        if (!(created instanceof Item)) {
            throw new IllegalStateException("Loader sourceComponent must produce an Item, got "
                + (created == null ? "null" : created.getClass().getName()));
        }
        attachLoadedItem(node, (Item) created);
        node.loadedComponent = sc;
        node.loadedSource = null;
    }

    private void attachLoadedItem(Loader node, Item child) {
        if (node.loadedItem != null) {
            node.children.remove(node.loadedItem);
        }
        node.loadedItem = child;
        child.parent.set(node);
        node.children.add(child);
        node.item.set(child);
    }

    private void clearLoadedItem(Loader node) {
        if (node.loadedItem != null) {
            node.children.remove(node.loadedItem);
            node.loadedItem = null;
        }
        node.item.set(null);
    }

    private Font font(float size) {
        return fontFor(size, null);
    }

    private Font fontFor(float size, String text) {
        Typeface tf = needsCjk(text) ? cjkTypeface() : null;
        if (tf == null) tf = defaultTypeface();
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
        if (cjkTypeface != null) {
            cjkTypeface.close();
            cjkTypeface = null;
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
