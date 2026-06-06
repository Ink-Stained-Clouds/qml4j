package io.qml4j.render;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.view.Component;
import io.qml4j.render.items.core.Flickable;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.view.Loader;
import io.qml4j.render.items.effect.ColorOverlay;
import io.qml4j.render.items.effect.DropShadow;
import io.qml4j.render.items.effect.Glow;
import io.qml4j.render.items.window.ApplicationWindow;
import io.qml4j.render.items.window.Button;
import io.qml4j.render.items.window.Control;
import io.qml4j.render.items.core.MouseArea;
import io.qml4j.render.items.input.TextField;
import io.qml4j.render.items.core.Text;
import io.qml4j.render.items.input.TextEdit;
import io.qml4j.render.items.input.TextInput;
import io.qml4j.render.items.core.TextWrap;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Renderer {

    private Paint paint;
    private ResourceLoader resources;
    private ComponentFactory factory;
    private final FontResolver fonts = new FontResolver();
    private final IconResolver icons = new IconResolver(fonts);
    private final TextLayout text = new TextLayout(fonts, icons);
    private final Painter painter = new Painter(this);

    public void setResourceLoader(ResourceLoader loader) {
        this.resources = loader;
        fonts.setResourceLoader(loader);
    }

    public void setComponentFactory(ComponentFactory factory) {
        this.factory = factory;
    }

    ResourceLoader resources() {
        return resources;
    }

    Paint paint() {
        if (paint == null) paint = new Paint();
        return paint;
    }

    // Max layout-settle iterations per frame. Converges multi-level implicit-size
    // chains; capped so a pathological oscillating layout can't spin forever.
    private static final int MAX_LAYOUT_PASSES = 8;

    public void render(Canvas canvas, Item root) {
        if (root == null) return;
        painter.bind(canvas);
        settleLayout(root);
        draw(canvas, root, 1f);
    }

    // Diagnostic/test hook: run the layout pre-pass without painting.
    public void layoutOnly(Item root) {
        if (root != null) settleLayout(root);
    }

    // Run the layout pre-pass and flush size-driven bindings until the tree
    // stops changing (or the cap is hit), so first-appearance layout is correct
    // on the very frame a node becomes visible instead of flashing for one frame.
    private void settleLayout(Item root) {
        DirtyQueue dq = DirtyQueue.current();
        for (int i = 0; i < MAX_LAYOUT_PASSES; i++) {
            measure(root);
            if (dq == null || dq.isEmpty()) break;
            dq.flush();
        }
    }

    // Layout pre-pass: populate implicitWidth/Height (text/control measurement),
    // run implicit-size following, container layout and anchors across the whole
    // tree so size-driven bindings can settle BEFORE painting.
    private void measure(Item node) {
        if (node == null || !node.visible.peek()) return;
        if (node instanceof Text) text.measureText((Text) node);
        if (node instanceof Control) text.measureControl((Control) node);
        followImplicitSize(node);
        // Children first so a container can size itself from their measured sizes.
        for (Item child : node.children) measure(child);
        runLayout(node);
        applyAnchors(node);
        updateChildrenRect(node);
    }

    private static void updateChildrenRect(Item node) {
        if (node.children.isEmpty()) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean any = false;
        for (Item c : node.children) {
            if (!c.visible.peek()) continue;
            float cx = c.x.peek().floatValue(), cy = c.y.peek().floatValue();
            float cw = c.width.peek().floatValue(), ch = c.height.peek().floatValue();
            minX = Math.min(minX, cx); minY = Math.min(minY, cy);
            maxX = Math.max(maxX, cx + cw); maxY = Math.max(maxY, cy + ch);
            any = true;
        }
        if (!any) return;
        node.childrenRect.x.set(minX);
        node.childrenRect.y.set(minY);
        node.childrenRect.width.set(maxX - minX);
        node.childrenRect.height.set(maxY - minY);
    }

    private static void runLayout(Item node) {
        node.layout();
    }

    private void draw(Canvas canvas, Item node, float inheritedAlpha) {
        if (!node.visible.peek()) return;
        drawForced(canvas, node, inheritedAlpha);
    }

    // Draw a node ignoring its own `visible` flag (used to render a MultiEffect
    // source, which is normally an invisible sibling rendered only via the effect).
    void drawForced(Canvas canvas, Item node, float inheritedAlpha) {
        if (node instanceof Text) text.measureText((Text) node);
        if (node instanceof Control) text.measureControl((Control) node);
        followImplicitSize(node);
        applyAnchors(node);
        if (node instanceof Loader) {
            resolveLoader((Loader) node);
        }
        runLayout(node);
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
            if (node instanceof ApplicationWindow) {
                ApplicationWindow win = (ApplicationWindow) node;
                float top = win.contentTop();
                float bottom = win.contentBottom(h);
                int contentSave = canvas.save();
                try {
                    canvas.clipRect(Rect.makeXYWH(0, top, w, Math.max(0f, bottom - top)));
                    canvas.translate(0, top);
                    for (Item child : zOrdered(node.children)) {
                        draw(canvas, child, alpha);
                    }
                } finally {
                    canvas.restoreToCount(contentSave);
                }
                drawChrome(canvas, win, w, h, alpha);
            } else {
                for (Item child : zOrdered(node.children)) {
                    draw(canvas, child, alpha);
                }
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

    // Qt: an Item's width follows implicitWidth until width is explicitly set.
    // We approximate "explicitly set" with an owns-check (current value equals
    // the last implicit value we wrote, or 0 if never written) plus the binding
    // flag, mirroring Text auto-measure. Not unit-testable (no headless trigger
    // beyond this pass); verified on device.
    private static void followImplicitSize(Item node) {
        double iw = node.implicitWidth.peek().doubleValue();
        if (iw > 0 && !node.width.isBound() && ownsImplicitWidth(node)) {
            node.width.set(iw);
            node.lastImplicitWidth = iw;
        }
        double ih = node.implicitHeight.peek().doubleValue();
        if (ih > 0 && !node.height.isBound() && ownsImplicitHeight(node)) {
            node.height.set(ih);
            node.lastImplicitHeight = ih;
        }
    }

    private static boolean ownsImplicitWidth(Item c) {
        if (Double.isNaN(c.lastImplicitWidth)) return c.width.peek().doubleValue() == 0.0;
        return c.width.peek().doubleValue() == c.lastImplicitWidth;
    }

    private static boolean ownsImplicitHeight(Item c) {
        if (Double.isNaN(c.lastImplicitHeight)) return c.height.peek().doubleValue() == 0.0;
        return c.height.peek().doubleValue() == c.lastImplicitHeight;
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
        node.paint(painter, w, h, alpha);
        if (node instanceof Button) {
            paintButton(canvas, (Button) node, w, h, alpha);
        } else if (node instanceof TextField) {
            paintTextField(canvas, (TextField) node, w, h, alpha);
        } else if (node instanceof TextInput) {
            paintTextInput(canvas, (TextInput) node, w, h, alpha);
        } else if (node instanceof TextEdit) {
            paintTextEdit(canvas, (TextEdit) node, w, h, alpha);
        } else if (node instanceof Text) {
            Text t = (Text) node;
            int color = applyAlpha(parseColor(t.color.peek()), alpha);
            paint().setColor(color);
            float size = t.effectiveFontSize();
            String ig = icons.iconGlyph(t);
            if (ig != null) {
                if (!ig.isEmpty()) {
                    try (Font f = new Font(fonts.iconTypeface(), size)) {
                        // Centre the glyph vertically in the node box using real
                        // font metrics (ascent is negative, descent positive).
                        FontMetrics fm = f.getMetrics();
                        float baseline = h / 2f - (fm.getAscent() + fm.getDescent()) / 2f;
                        canvas.drawString(ig, 0, baseline, f, paint);
                    }
                }
                return;
            }
            String s = icons.displayText(t);
            if (s.isEmpty()) return;
            boolean elideRight = t.elide.peek().intValue() == 3; // Text.ElideRight
            String wrapMode = text.wrapModeString(t.wrapMode.peek().intValue());
            try (Font font = fonts.fontFor(size, s)) {
                String[] lines = (wrapMode != null && w > 0f)
                    ? TextWrap.wrap(s, wrapMode, w, seg -> font.measureTextWidth(seg))
                          .lines.toArray(new String[0])
                    : text.splitLines(s);
                float lineH = text.lineHeight(font);
                float baseline0 = text.baselineInLine(font);
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].isEmpty()) continue;
                    String line = elideRight ? text.elideToWidth(lines[i], font, w) : lines[i];
                    canvas.drawString(line, 0, baseline0 + i * lineH, font, paint);
                }
            }
        }
    }

    private void drawChrome(Canvas canvas, ApplicationWindow win, float w, float h, float alpha) {
        win.layoutChrome(w, h);
        Item m = win.menuBar.peek();
        Item hdr = win.header.peek();
        Item ftr = win.footer.peek();
        if (m != null) draw(canvas, m, alpha);
        if (hdr != null) draw(canvas, hdr, alpha);
        if (ftr != null) draw(canvas, ftr, alpha);
    }

    private void paintButton(Canvas canvas, Button b, float w, float h, float alpha) {
        boolean enabled = !Boolean.FALSE.equals(b.enabled.peek());
        boolean down = Boolean.TRUE.equals(b.down.peek()) || Boolean.TRUE.equals(b.checked.peek());
        float a = enabled ? alpha : alpha * 0.5f;
        float radius = Math.max(0f, b.radius.peek().floatValue());
        int bg = parseColor(down ? b.downColor.peek() : b.color.peek());
        Paint p = paint();
        p.setShader(null);
        p.setMode(PaintMode.FILL);
        p.setColor(applyAlpha(bg, a));
        if (radius > 0f) {
            canvas.drawRRect(RRect.makeXYWH(0, 0, w, h, radius), p);
        } else {
            canvas.drawRect(Rect.makeXYWH(0, 0, w, h), p);
        }
        String label = b.text.peek();
        if (label == null || label.isEmpty()) return;
        float size = b.fontSize.peek().floatValue();
        try (Font font = fonts.fontFor(size, label)) {
            float tw = font.measureTextWidth(label);
            float tx = (w - tw) / 2f;
            float ty = text.centeredBaseline(font, h);
            p.setColor(applyAlpha(parseColor(b.textColor.peek()), a));
            canvas.drawString(label, tx, ty, font, p);
        }
    }

    private void paintTextField(Canvas canvas, TextField tf, float w, float h, float alpha) {
        float radius = Math.max(0f, tf.radius.peek().floatValue());
        Paint p = paint();
        p.setShader(null);
        p.setMode(PaintMode.FILL);
        p.setColor(applyAlpha(parseColor(tf.backgroundColor.peek()), alpha));
        if (radius > 0f) {
            canvas.drawRRect(RRect.makeXYWH(0, 0, w, h, radius), p);
        } else {
            canvas.drawRect(Rect.makeXYWH(0, 0, w, h), p);
        }
        float bw = Math.max(0f, tf.borderWidth.peek().floatValue());
        if (bw > 0f) {
            boolean focused = Boolean.TRUE.equals(tf.activeFocus.peek());
            int bc = parseColor(focused ? tf.focusBorderColor.peek() : tf.borderColor.peek());
            p.setMode(PaintMode.STROKE);
            p.setStrokeWidth(bw);
            p.setColor(applyAlpha(bc, alpha));
            float inset = bw / 2f;
            float iw = Math.max(0f, w - bw);
            float ih = Math.max(0f, h - bw);
            if (radius > 0f) {
                canvas.drawRRect(RRect.makeXYWH(inset, inset, iw, ih, Math.max(0f, radius - inset)), p);
            } else {
                canvas.drawRect(Rect.makeXYWH(inset, inset, iw, ih), p);
            }
            p.setMode(PaintMode.FILL);
        }
        float size = tf.fontSize.peek().floatValue();
        float pad = tf.padding.peek().floatValue();
        int tfSave = canvas.save();
        try {
            canvas.translate(pad, 0);
            String s = tf.text.peek();
            if (s == null || s.isEmpty()) {
                String ph = tf.placeholderText.peek();
                if (ph != null && !ph.isEmpty()) {
                    try (Font font = fonts.fontFor(size, ph)) {
                        p.setColor(applyAlpha(parseColor(tf.placeholderTextColor.peek()), alpha));
                        canvas.drawString(ph, 0, text.centeredBaseline(font, h), font, p);
                    }
                }
            }
            paintTextInput(canvas, tf, w, h, alpha);
        } finally {
            canvas.restoreToCount(tfSave);
        }
    }

    private void paintTextInput(Canvas canvas, TextInput ti, float w, float h, float alpha) {
        String s = ti.text.peek();
        if (s == null) s = "";
        float size = ti.fontSize.peek().floatValue();
        try (Font font = fonts.fontFor(size, s)) {
            float baseline = text.centeredBaseline(font, h);
            float glyphTop = baseline + text.glyphTopOffset(font);
            float glyphHeight = text.glyphExtent(font);
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

    private void paintTextEdit(Canvas canvas, TextEdit te, float w, float h, float alpha) {
        String s = te.text.peek();
        if (s == null) s = "";
        float size = te.fontSize.peek().floatValue();
        try (Font font = fonts.fontFor(size, s)) {
            TextWrap.Result wrapped = text.wrapFor(te, s, w, size, font);
            te.lineCount.set(wrapped.lines.size());
            float lineH = text.lineHeight(font);
            float total = lineH * wrapped.lines.size();
            float yOffset = text.topOffset(te.verticalAlignment.peek(), h, total);
            paintSelectionMultiline(canvas, te, wrapped, font, yOffset, lineH, size, alpha);
            paint().setColor(applyAlpha(parseColor(te.color.peek()), alpha));
            for (int i = 0; i < wrapped.lines.size(); i++) {
                String line = wrapped.lines.get(i);
                if (!line.isEmpty()) {
                    float baseline = yOffset + i * lineH + text.baselineInLine(font);
                    canvas.drawString(line, 0, baseline, font, paint);
                }
            }
            if (Boolean.TRUE.equals(te.activeFocus.peek()) && caretBlinkOn()) {
                drawCaretMultiline(canvas, te, wrapped, font, yOffset, lineH, size, alpha);
            }
        }
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
        float glyphTop = text.baselineInLine(font) + text.glyphTopOffset(font);
        float glyphHeight = text.glyphExtent(font);
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
        float glyphTop = text.baselineInLine(font) + text.glyphTopOffset(font);
        float glyphHeight = text.glyphExtent(font);
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
        try (Font font = fonts.fontFor(size, s)) {
            float w = te.width.peek().floatValue();
            TextWrap.Result wrapped = text.wrapFor(te, s, w, size, font);
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
        try (Font font = fonts.fontFor(size, s)) {
            float w = te.width.peek().floatValue();
            float h = te.height.peek().floatValue();
            TextWrap.Result wrapped = text.wrapFor(te, s, w, size, font);
            float lineH = text.lineHeight(font);
            float total = lineH * wrapped.lines.size();
            float yOffset = text.topOffset(te.verticalAlignment.peek(), h, total);
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
        if (ti instanceof TextField) {
            localX -= ((TextField) ti).padding.peek().floatValue();
        }
        if (s == null || s.isEmpty() || localX <= 0) return 0;
        float size = ti.fontSize.peek().floatValue();
        try (Font font = fonts.fontFor(size, s)) {
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
        QObject created = df.create(0, null, node);
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

    public void dispose() {
        if (paint != null) {
            paint.close();
            paint = null;
        }
        fonts.close();
    }

    // Common CSS/QML named colors. "transparent" is the critical one (MD3 uses it
    // heavily); without it parseColor returned opaque black and painted over.
    private static final java.util.Map<String, Integer> NAMED_COLORS = buildNamedColors();

    private static java.util.Map<String, Integer> buildNamedColors() {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        m.put("transparent", 0x00000000);
        m.put("black", 0xFF000000);
        m.put("white", 0xFFFFFFFF);
        m.put("red", 0xFFFF0000);
        m.put("green", 0xFF008000);
        m.put("blue", 0xFF0000FF);
        m.put("gray", 0xFF808080);
        m.put("grey", 0xFF808080);
        m.put("yellow", 0xFFFFFF00);
        m.put("orange", 0xFFFFA500);
        return m;
    }

    public static int parseColor(String s) {
        if (s == null) return 0xFF000000;
        s = s.trim();
        if (s.isEmpty()) return 0xFF000000;
        if (s.charAt(0) != '#') {
            Integer named = NAMED_COLORS.get(s.toLowerCase());
            return named != null ? named : 0xFF000000;
        }
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

    static int applyAlpha(int color, float alpha) {
        if (alpha >= 1f) return color;
        if (alpha <= 0f) return color & 0x00FFFFFF;
        int a = (color >>> 24) & 0xFF;
        int na = Math.round(a * alpha);
        return (na << 24) | (color & 0x00FFFFFF);
    }

    static float sigma(float radius) {
        return radius <= 0f ? 0f : radius / 2f;
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
}
