package io.qml4j.render;

import io.qml4j.render.items.Column;
import io.qml4j.render.items.Image;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.Loader;
import io.qml4j.render.items.MouseArea;
import io.qml4j.render.items.Rectangle;
import io.qml4j.render.items.Row;
import io.qml4j.render.items.Text;

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

        int savedCount = canvas.save();
        try {
            canvas.translate(x, y);
            paintNode(canvas, node, w, h, alpha);
            for (Item child : node.children) {
                draw(canvas, child, alpha);
            }
        } finally {
            canvas.restoreToCount(savedCount);
        }
    }

    private void measureText(Text t) {
        float size = t.fontSize.peek().floatValue();
        String s = t.text.peek();
        if (s == null) s = "";
        boolean canMeasureW = !t.width.isBound() && ownsWidth(t);
        boolean canMeasureH = !t.height.isBound() && ownsHeight(t);
        if (!canMeasureW && !canMeasureH) return;
        if (s.equals(t.lastMeasuredText) && size == t.lastMeasuredSize) return;
        try (Font font = font(size)) {
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
            Rectangle r = (Rectangle) node;
            int color = applyAlpha(parseColor(r.color.peek()), alpha);
            paint().setColor(color);
            canvas.drawRect(Rect.makeXYWH(0, 0, w, h), paint);
        } else if (node instanceof Image) {
            paintImage(canvas, (Image) node, w, h, alpha);
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
        String src = node.source.peek();
        if (src == null || src.isEmpty()) {
            if (node.loadedItem != null) {
                node.children.remove(node.loadedItem);
                node.loadedItem = null;
                node.loadedSource = null;
                node.item.set(null);
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
        if (node.loadedItem != null) {
            node.children.remove(node.loadedItem);
        }
        node.loadedItem = child;
        node.loadedSource = src;
        child.parent.set(node);
        node.children.add(child);
        node.item.set(child);
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
