package io.qml4j.render;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.view.Component;
import io.qml4j.render.items.core.Flickable;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.view.Loader;
import io.qml4j.render.items.effect.ColorOverlay;
import io.qml4j.render.items.effect.DropShadow;
import io.qml4j.render.items.effect.Glow;
import io.qml4j.render.items.window.ApplicationWindow;
import io.qml4j.render.items.input.TextField;
import io.qml4j.render.items.input.TextEdit;
import io.qml4j.render.items.input.TextInput;
import io.qml4j.render.items.core.TextWrap;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    FontResolver fonts() {
        return fonts;
    }

    IconResolver icons() {
        return icons;
    }

    TextLayout textLayout() {
        return text;
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
        // Resolve a Loader before measuring its children so the loaded item is in the
        // tree + measured, and the Loader can size to it in this same layout pass.
        if (node instanceof Loader) resolveLoader((Loader) node);
        node.measure(text);
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
            float cx = c.x.peekFloat(), cy = c.y.peekFloat();
            float cw = c.width.peekFloat(), ch = c.height.peekFloat();
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
        node.measure(text);
        followImplicitSize(node);
        applyAnchors(node);
        if (node instanceof Loader) {
            resolveLoader((Loader) node);
        }
        runLayout(node);
        float x = node.x.peekFloat();
        float y = node.y.peekFloat();
        float w = node.width.peekFloat();
        float h = node.height.peekFloat();
        float alpha = inheritedAlpha * node.opacity.peekFloat();
        if (alpha <= 0f) return;
        float rot = node.rotation.peekFloat();
        float sc = node.scale.peekFloat();
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
            node.paint(painter, w, h, alpha);
            if (node instanceof Flickable) {
                Flickable f = (Flickable) node;
                canvas.clipRect(Rect.makeXYWH(0, 0, w, h));
                canvas.translate(-f.contentX.peekFloat(), -f.contentY.peekFloat());
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
            if (children.get(i).z.peekFloat() != 0f) { anyZ = true; break; }
        }
        if (!anyZ) return children;
        List<Item> copy = new ArrayList<>(children);
        copy.sort(Comparator.comparingDouble(c -> c.z.peekDouble()));
        return copy;
    }

    // Qt: an Item's width follows implicitWidth until width is explicitly set.
    // We approximate "explicitly set" with an owns-check (current value equals
    // the last implicit value we wrote, or 0 if never written) plus the binding
    // flag, mirroring Text auto-measure. Not unit-testable (no headless trigger
    // beyond this pass); verified on device.
    private static void followImplicitSize(Item node) {
        double iw = node.implicitWidth.peekDouble();
        if (iw > 0 && !node.width.isBound() && ownsImplicitWidth(node)) {
            node.width.set(iw);
            node.lastImplicitWidth = iw;
        }
        double ih = node.implicitHeight.peekDouble();
        if (ih > 0 && !node.height.isBound() && ownsImplicitHeight(node)) {
            node.height.set(ih);
            node.lastImplicitHeight = ih;
        }
    }

    private static boolean ownsImplicitWidth(Item c) {
        if (Double.isNaN(c.lastImplicitWidth)) return c.width.peekDouble() == 0.0;
        return c.width.peekDouble() == c.lastImplicitWidth;
    }

    private static boolean ownsImplicitHeight(Item c) {
        if (Double.isNaN(c.lastImplicitHeight)) return c.height.peekDouble() == 0.0;
        return c.height.peekDouble() == c.lastImplicitHeight;
    }

    static void applyAnchors(Item node) {
        Anchors a = node.anchors;
        float baseM = a.margins.peekFloat();
        float lm = marginOr(a.leftMargin.peek(), baseM);
        float rm = marginOr(a.rightMargin.peek(), baseM);
        float tm = marginOr(a.topMargin.peek(), baseM);
        float bm = marginOr(a.bottomMargin.peek(), baseM);

        Item fill = a.fill.peek();
        if (fill != null) {
            node.x.set(lm);
            node.y.set(tm);
            node.width.set(fill.width.peekFloat() - lm - rm);
            node.height.set(fill.height.peekFloat() - tm - bm);
            return;
        }
        Item ci = a.centerIn.peek();
        if (ci != null) {
            float w = node.width.peekFloat();
            float h = node.height.peekFloat();
            node.x.set((ci.width.peekFloat() - w) / 2f);
            node.y.set((ci.height.peekFloat() - h) / 2f);
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
            float w = node.width.peekFloat();
            node.x.set(resolveX(right, node) - rm - w);
        } else if (hcenter != null) {
            float w = node.width.peekFloat();
            float off = a.horizontalCenterOffset.peekFloat();
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
            float h = node.height.peekFloat();
            node.y.set(resolveY(bottom, node) - bm - h);
        } else if (vcenter != null) {
            float h = node.height.peekFloat();
            float off = a.verticalCenterOffset.peekFloat();
            node.y.set(resolveY(vcenter, node) - h / 2f + off);
        }
    }

    private static float resolveX(AnchorLine line, Item node) {
        Item src = line.source;
        boolean srcIsParent = src == node.parent.peek();
        float base = srcIsParent ? 0f : src.x.peekFloat();
        float w = src.width.peekFloat();
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
        float base = srcIsParent ? 0f : src.y.peekFloat();
        float h = src.height.peekFloat();
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

    private void drawChrome(Canvas canvas, ApplicationWindow win, float w, float h, float alpha) {
        win.layoutChrome(w, h);
        Item m = win.menuBar.peek();
        Item hdr = win.header.peek();
        Item ftr = win.footer.peek();
        if (m != null) draw(canvas, m, alpha);
        if (hdr != null) draw(canvas, hdr, alpha);
        if (ftr != null) draw(canvas, ftr, alpha);
    }

    public int moveCaretVerticalForTextEdit(TextEdit te, int caret, int delta) {
        String s = te.text.peek();
        if (s == null) s = "";
        float size = te.fontSize.peekFloat();
        try (Font font = fonts.fontFor(size, s)) {
            float w = te.width.peekFloat();
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
        float size = te.fontSize.peekFloat();
        try (Font font = fonts.fontFor(size, s)) {
            float w = te.width.peekFloat();
            float h = te.height.peekFloat();
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
            localX -= ((TextField) ti).padding.peekFloat();
        }
        if (s == null || s.isEmpty() || localX <= 0) return 0;
        float size = ti.fontSize.peekFloat();
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
        String qml = new String(bytes, StandardCharsets.UTF_8);
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
    private static final Map<String, Integer> NAMED_COLORS = buildNamedColors();

    private static Map<String, Integer> buildNamedColors() {
        Map<String, Integer> m = new HashMap<>();
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
                d.offsetX.peekFloat(), d.offsetY.peekFloat(),
                sigma(d.radius.peekFloat()), sigma(d.radius.peekFloat()),
                parseColor(d.color.peek())));
        } else if (effect instanceof Glow) {
            Glow g = (Glow) effect;
            p.setImageFilter(ImageFilter.makeDropShadow(
                0f, 0f, sigma(g.radius.peekFloat()), sigma(g.radius.peekFloat()),
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
            float r = d.radius.peekFloat();
            float ox = Math.abs(d.offsetX.peekFloat());
            float oy = Math.abs(d.offsetY.peekFloat());
            return r + Math.max(ox, oy) + 4f;
        }
        if (effect instanceof Glow) {
            return ((Glow) effect).radius.peekFloat() + 4f;
        }
        return 0f;
    }
}
