package io.qml4j.render;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.QObject;
import io.qml4j.render.items.Column;
import io.qml4j.render.items.Component;
import io.qml4j.render.items.Flickable;
import io.qml4j.render.items.Gradient;
import io.qml4j.render.items.GradientStop;
import io.qml4j.render.items.Image;
import io.qml4j.render.items.ImageFill;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Loader;
import io.qml4j.render.items.ColorOverlay;
import io.qml4j.render.items.DropShadow;
import io.qml4j.render.items.Glow;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.PathArc;
import io.qml4j.render.items.PathCubic;
import io.qml4j.render.items.PathElement;
import io.qml4j.render.items.PathLine;
import io.qml4j.render.items.PathMove;
import io.qml4j.render.items.PathQuad;
import io.qml4j.render.items.Rectangle;
import io.qml4j.render.items.Shape;
import io.qml4j.render.items.ShapePath;
import io.qml4j.render.items.Row;
import io.qml4j.render.items.Text;
import io.qml4j.render.items.TextEdit;
import io.qml4j.render.items.TextInput;
import io.qml4j.render.items.TextWrap;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PathDirection;
import io.github.humbleui.skija.PathEllipseArc;
import io.github.humbleui.skija.PathFillMode;
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
        Paint layerPaint = layerEffectPaint(node);
        try {
            canvas.translate(x, y);
            applyTransform(canvas, w, h, rot, sc);
            if (layerPaint != null) {
                float m = layerEffectMargin(node);
                canvas.saveLayer(Rect.makeXYWH(-m, -m, w + 2 * m, h + 2 * m), layerPaint);
            }
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
            if (layerPaint != null) layerPaint.close();
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
        String[] lines = splitLines(s);
        try (Font font = fontFor(size, s)) {
            if (canMeasureW) {
                float w = 0f;
                for (String line : lines) {
                    float lw = font.measureTextWidth(line);
                    if (lw > w) w = lw;
                }
                t.width.set(w);
                t.lastSetWidth = w;
            }
            if (canMeasureH) {
                float h = size * lines.length;
                t.height.set(h);
                t.lastSetHeight = h;
            }
        }
        t.lastMeasuredText = s;
        t.lastMeasuredSize = size;
    }

    private static String[] splitLines(String s) {
        if (s == null || s.isEmpty()) return new String[]{""};
        return s.split("\n", -1);
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
        } else if (node instanceof Shape) {
            paintShape(canvas, (Shape) node, w, h, alpha);
        } else if (node instanceof Image) {
            paintImage(canvas, (Image) node, w, h, alpha);
        } else if (node instanceof TextInput) {
            paintTextInput(canvas, (TextInput) node, w, h, alpha);
        } else if (node instanceof TextEdit) {
            paintTextEdit(canvas, (TextEdit) node, w, h, alpha);
        } else if (node instanceof Text) {
            Text t = (Text) node;
            int color = applyAlpha(parseColor(t.color.peek()), alpha);
            paint().setColor(color);
            float size = t.fontSize.peek().floatValue();
            String s = t.text.peek();
            if (s == null || s.isEmpty()) return;
            String[] lines = splitLines(s);
            try (Font font = fontFor(size, s)) {
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].isEmpty()) continue;
                    canvas.drawString(lines[i], 0, size * (i + 1), font, paint);
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
                p.setColor(applyAlpha(parseColor(ti.color.peek()), alpha));
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

    private void paintTextEdit(Canvas canvas, TextEdit te, float w, float h, float alpha) {
        String s = te.text.peek();
        if (s == null) s = "";
        float size = te.fontSize.peek().floatValue();
        try (Font font = fontFor(size, s)) {
            TextWrap.Result wrapped = wrapFor(te, s, w, size, font);
            te.lineCount.set(wrapped.lines.size());
            float lineH = size * (GLYPH_DESCENT_RATIO - GLYPH_ASCENT_RATIO) + size * 0.2f;
            float total = lineH * wrapped.lines.size();
            float yOffset = topOffset(te.verticalAlignment.peek(), h, total);
            paintSelectionMultiline(canvas, te, wrapped, font, yOffset, lineH, size, alpha);
            paint().setColor(applyAlpha(parseColor(te.color.peek()), alpha));
            for (int i = 0; i < wrapped.lines.size(); i++) {
                String line = wrapped.lines.get(i);
                if (!line.isEmpty()) {
                    float baseline = yOffset + i * lineH + size;
                    canvas.drawString(line, 0, baseline, font, paint);
                }
            }
            if (Boolean.TRUE.equals(te.activeFocus.peek()) && caretBlinkOn()) {
                drawCaretMultiline(canvas, te, wrapped, font, yOffset, lineH, size, alpha);
            }
        }
    }

    private TextWrap.Result wrapFor(TextEdit te, String s, float width, float size, Font font) {
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

    private float topOffset(String align, float h, float total) {
        if ("AlignVCenter".equals(align)) return Math.max(0, (h - total) / 2f);
        if ("AlignBottom".equals(align)) return Math.max(0, h - total);
        return 0;
    }

    private void paintSelectionMultiline(Canvas canvas, TextEdit te, TextWrap.Result wrapped,
                                         Font font, float yOffset, float lineH, float size, float alpha) {
        int len = te.cachedText == null ? 0 : te.cachedText.length();
        int selS = Math.max(0, Math.min(te.selectionStart.peek().intValue(), len));
        int selE = Math.max(selS, Math.min(te.selectionEnd.peek().intValue(), len));
        if (selE <= selS) return;
        Paint p = paint();
        p.setMode(PaintMode.FILL);
        p.setColor(applyAlpha(parseColor(te.selectionColor.peek()), alpha));
        float glyphTop = size + size * GLYPH_ASCENT_RATIO;
        float glyphHeight = size * (GLYPH_DESCENT_RATIO - GLYPH_ASCENT_RATIO);
        for (int i = 0; i < wrapped.lines.size(); i++) {
            int ls = wrapped.starts[i];
            String line = wrapped.lines.get(i);
            int le = ls + line.length();
            if (selE <= ls || selS >= le) continue;
            int a = Math.max(selS, ls) - ls;
            int b = Math.min(selE, le) - ls;
            float x0 = a == 0 ? 0 : font.measureTextWidth(line.substring(0, a));
            float x1 = font.measureTextWidth(line.substring(0, b));
            float y = yOffset + i * lineH + glyphTop;
            canvas.drawRect(Rect.makeXYWH(x0, y, x1 - x0, glyphHeight), p);
        }
    }

    private void drawCaretMultiline(Canvas canvas, TextEdit te, TextWrap.Result wrapped,
                                    Font font, float yOffset, float lineH, float size, float alpha) {
        int len = te.cachedText == null ? 0 : te.cachedText.length();
        int pos = Math.max(0, Math.min(te.cursorPosition.peek().intValue(), len));
        int lineIdx = TextWrap.lineForCaret(wrapped, pos);
        String line = wrapped.lines.get(lineIdx);
        int col = Math.max(0, Math.min(pos - wrapped.starts[lineIdx], line.length()));
        float cx = col == 0 ? 0 : font.measureTextWidth(line.substring(0, col));
        float glyphTop = size + size * GLYPH_ASCENT_RATIO;
        float glyphHeight = size * (GLYPH_DESCENT_RATIO - GLYPH_ASCENT_RATIO);
        Paint p = paint();
        p.setMode(PaintMode.FILL);
        p.setColor(applyAlpha(parseColor(te.color.peek()), alpha));
        float cw = Math.max(1f, size / 16f);
        canvas.drawRect(Rect.makeXYWH(cx, yOffset + lineIdx * lineH + glyphTop, cw, glyphHeight), p);
    }

    public int moveCaretVerticalForTextEdit(TextEdit te, int caret, int delta) {
        String s = te.text.peek();
        if (s == null) s = "";
        float size = te.fontSize.peek().floatValue();
        try (Font font = fontFor(size, s)) {
            float w = te.width.peek().floatValue();
            TextWrap.Result wrapped = wrapFor(te, s, w, size, font);
            return TextWrap.moveCaretVertical(wrapped, caret, delta,
                seg -> font.measureTextWidth(seg));
        } catch (Throwable ignored) {
            return caret;
        }
    }

    public int caretIndexForTextEdit(TextEdit te, float localX, float localY) {
        String s = te.text.peek();
        if (s == null) s = "";
        float size = te.fontSize.peek().floatValue();
        try (Font font = fontFor(size, s)) {
            float w = te.width.peek().floatValue();
            float h = te.height.peek().floatValue();
            TextWrap.Result wrapped = wrapFor(te, s, w, size, font);
            float lineH = size * (GLYPH_DESCENT_RATIO - GLYPH_ASCENT_RATIO) + size * 0.2f;
            float total = lineH * wrapped.lines.size();
            float yOffset = topOffset(te.verticalAlignment.peek(), h, total);
            int lineIdx = (int) Math.floor((localY - yOffset) / lineH);
            if (lineIdx < 0) lineIdx = 0;
            if (lineIdx >= wrapped.lines.size()) lineIdx = wrapped.lines.size() - 1;
            String line = wrapped.lines.get(lineIdx);
            int col = TextWrap.caretInLine(line, localX, seg -> font.measureTextWidth(seg));
            return wrapped.starts[lineIdx] + col;
        } catch (Throwable ignored) {
            return s.length();
        }
    }

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
        ImageFill.Plan plan = ImageFill.compute(node.fillMode.peek(), iw, ih, w, h);
        if (plan == null) return;
        node.paintedWidth.set(plan.paintedWidth);
        node.paintedHeight.set(plan.paintedHeight);
        switch (plan.op) {
            case DRAW_RECT:
                drawImagePlan(canvas, node.skiaImage, plan);
                break;
            case TILE_X:
            case TILE_Y:
            case TILE_XY:
                drawTilePlan(canvas, node.skiaImage, plan, w, h);
                break;
        }
    }

    private void drawImagePlan(Canvas canvas, io.github.humbleui.skija.Image img, ImageFill.Plan plan) {
        Rect src = Rect.makeXYWH(plan.srcX, plan.srcY, plan.srcW, plan.srcH);
        Rect dst = Rect.makeXYWH(plan.dstX, plan.dstY, plan.dstW, plan.dstH);
        canvas.drawImageRect(img, src, dst);
    }

    private void drawTilePlan(Canvas canvas, io.github.humbleui.skija.Image img,
                              ImageFill.Plan plan, float boundsW, float boundsH) {
        int saved = canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(plan.clipX, plan.clipY, plan.clipW, plan.clipH));
            float stepX = plan.tileStepX > 0 ? plan.tileStepX : boundsW;
            float stepY = plan.tileStepY > 0 ? plan.tileStepY : boundsH;
            Rect src = Rect.makeXYWH(plan.srcX, plan.srcY, plan.srcW, plan.srcH);
            for (float y = 0; y < boundsH; y += stepY) {
                for (float x = 0; x < boundsW; x += stepX) {
                    Rect dst = Rect.makeXYWH(x, y, plan.dstW, plan.dstH);
                    canvas.drawImageRect(img, src, dst);
                }
            }
        } finally {
            canvas.restoreToCount(saved);
        }
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

    public static int parseColor(String s) {
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

    private void paintShape(Canvas canvas, Shape shape, float w, float h, float alpha) {
        try (Paint p = new Paint()) {
            p.setAntiAlias(true);
            for (ShapePath sp : shape.elements) {
                try (Path path = buildPath(sp)) {
                    fillPath(canvas, path, sp, alpha, p);
                    strokePath(canvas, path, sp, alpha, p);
                }
            }
        }
    }

    private Paint layerEffectPaint(Item node) {
        if (!Boolean.TRUE.equals(node.layer.enabled.peek())) return null;
        Object effect = node.layer.effect.peek();
        if (effect == null) return null;
        Paint p = new Paint();
        if (effect instanceof DropShadow) {
            DropShadow d = (DropShadow) effect;
            p.setImageFilter(ImageFilter.makeDropShadow(
                d.offsetX.peek().floatValue(), d.offsetY.peek().floatValue(),
                sigma(d.radius.peek().floatValue()), sigma(d.radius.peek().floatValue()),
                parseColor(d.color.peek())));
        } else if (effect instanceof Glow) {
            Glow g = (Glow) effect;
            p.setImageFilter(ImageFilter.makeDropShadow(
                0f, 0f, sigma(g.radius.peek().floatValue()), sigma(g.radius.peek().floatValue()),
                parseColor(g.color.peek())));
        } else if (effect instanceof ColorOverlay) {
            ColorOverlay c = (ColorOverlay) effect;
            p.setColorFilter(ColorFilter.makeBlend(parseColor(c.color.peek()), BlendMode.SRC_IN));
        } else {
            p.close();
            return null;
        }
        return p;
    }

    private static float layerEffectMargin(Item node) {
        Object effect = node.layer.effect.peek();
        if (effect instanceof DropShadow) {
            DropShadow d = (DropShadow) effect;
            float r = d.radius.peek().floatValue();
            float ox = Math.abs(d.offsetX.peek().floatValue());
            float oy = Math.abs(d.offsetY.peek().floatValue());
            return r + Math.max(ox, oy) + 4f;
        }
        if (effect instanceof Glow) {
            return ((Glow) effect).radius.peek().floatValue() + 4f;
        }
        return 0f;
    }

    private static float sigma(float radius) {
        return radius <= 0f ? 0f : radius / 2f;
    }

    private Path buildPath(ShapePath sp) {
        PathBuilder pb = new PathBuilder();
        pb.setFillMode("WindingFill".equals(sp.fillRule.peek())
            ? PathFillMode.WINDING : PathFillMode.EVEN_ODD);
        pb.moveTo(sp.startX.peek().floatValue(), sp.startY.peek().floatValue());
        for (PathElement e : sp.pathElements) {
            appendElement(pb, e);
        }
        return pb.build();
    }

    private void appendElement(PathBuilder pb, PathElement e) {
        if (e instanceof PathLine) {
            PathLine l = (PathLine) e;
            pb.lineTo(l.x.peek().floatValue(), l.y.peek().floatValue());
        } else if (e instanceof PathMove) {
            PathMove m = (PathMove) e;
            pb.moveTo(m.x.peek().floatValue(), m.y.peek().floatValue());
        } else if (e instanceof PathQuad) {
            PathQuad q = (PathQuad) e;
            pb.quadTo(q.controlX.peek().floatValue(), q.controlY.peek().floatValue(),
                      q.x.peek().floatValue(), q.y.peek().floatValue());
        } else if (e instanceof PathCubic) {
            PathCubic c = (PathCubic) e;
            pb.cubicTo(c.control1X.peek().floatValue(), c.control1Y.peek().floatValue(),
                       c.control2X.peek().floatValue(), c.control2Y.peek().floatValue(),
                       c.x.peek().floatValue(), c.y.peek().floatValue());
        } else if (e instanceof PathArc) {
            PathArc a = (PathArc) e;
            PathEllipseArc size = Boolean.TRUE.equals(a.useLargeArc.peek())
                ? PathEllipseArc.LARGER : PathEllipseArc.SMALLER;
            PathDirection dir = "Counterclockwise".equals(a.direction.peek())
                ? PathDirection.COUNTER_CLOCKWISE : PathDirection.CLOCKWISE;
            pb.ellipticalArcTo(a.radiusX.peek().floatValue(), a.radiusY.peek().floatValue(),
                               a.xAxisRotation.peek().floatValue(), size, dir,
                               a.x.peek().floatValue(), a.y.peek().floatValue());
        }
    }

    private void fillPath(Canvas canvas, Path path, ShapePath sp, float alpha, Paint p) {
        int argb = shapeArgb(sp.fillColor.peek(), alpha);
        if (argb == 0) return;
        p.setColor(argb);
        p.setMode(PaintMode.FILL);
        canvas.drawPath(path, p);
    }

    private void strokePath(Canvas canvas, Path path, ShapePath sp, float alpha, Paint p) {
        float sw = sp.strokeWidth.peek().floatValue();
        if (sw <= 0f) return;
        int argb = shapeArgb(sp.strokeColor.peek(), alpha);
        if (argb == 0) return;
        p.setColor(argb);
        p.setMode(PaintMode.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(mapCap(sp.capStyle.peek()));
        p.setStrokeJoin(mapJoin(sp.joinStyle.peek()));
        canvas.drawPath(path, p);
    }

    private static int shapeArgb(String colorStr, float alpha) {
        if (colorStr == null || "transparent".equals(colorStr)) return 0;
        int rgb = parseColor(colorStr);
        int a = (int) (((rgb >>> 24) & 0xFF) * alpha);
        if (a <= 0) return 0;
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static PaintStrokeCap mapCap(String cap) {
        if ("RoundCap".equals(cap)) return PaintStrokeCap.ROUND;
        if ("FlatCap".equals(cap)) return PaintStrokeCap.BUTT;
        return PaintStrokeCap.SQUARE;
    }

    private static PaintStrokeJoin mapJoin(String join) {
        if ("RoundJoin".equals(join)) return PaintStrokeJoin.ROUND;
        if ("MiterJoin".equals(join)) return PaintStrokeJoin.MITER;
        return PaintStrokeJoin.BEVEL;
    }
}
