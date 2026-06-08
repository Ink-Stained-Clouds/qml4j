package io.qml4j.render;

import io.qml4j.engine.DelegateFactory;
import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.view.Component;
import io.qml4j.render.items.core.Flickable;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.Rectangle;
import io.qml4j.render.items.transform.Rotation;
import io.qml4j.render.items.transform.Scale;
import io.qml4j.render.items.transform.Transform;
import io.qml4j.render.items.transform.Translate;
import io.qml4j.render.items.view.Loader;
import io.qml4j.render.items.effect.ColorOverlay;
import io.qml4j.render.items.effect.DropShadow;
import io.qml4j.render.items.effect.MultiEffect;
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
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathOp;
import io.github.humbleui.types.RRect;
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

    // When true, the current frame's draw reuses the geometry from the last full layout
    // (an idle frame: nothing in the scene changed). Set by render(skipLayout=true).
    private boolean skipLayout;

    // The GPU context of the surface being rendered, threaded to Painter so a Canvas item's
    // offscreen backing is made on the same context (a raster offscreen won't blit on GPU).
    private io.github.humbleui.skija.DirectContext gpuContext;

    public void setGpuContext(io.github.humbleui.skija.DirectContext ctx) {
        this.gpuContext = ctx;
    }

    io.github.humbleui.skija.DirectContext gpuContext() {
        return gpuContext;
    }

    public void render(Canvas canvas, Item root) {
        render(canvas, root, false);
    }

    // skipLayout: the scene is unchanged since the last frame, so skip the layout pass and
    // the per-node measure/layout in draw, repainting with cached geometry. The host's game
    // loop calls renderFrame every frame; this keeps an idle UI to paint-only cost.
    public void render(Canvas canvas, Item root, boolean skipLayout) {
        if (root == null) return;
        painter.bind(canvas);
        this.skipLayout = skipLayout;
        if (!skipLayout) settleLayout(root);
        draw(canvas, root, 1f);
    }

    // Opt-in dev FPS overlay (-Dqml4j.fps=true), drawn top-right over the scene.
    private static final boolean FPS_OVERLAY = Boolean.getBoolean("qml4j.fps");

    public boolean fpsOverlayEnabled() {
        return FPS_OVERLAY;
    }

    public void drawFpsOverlay(Canvas canvas, float w, double fps) {
        String s = Math.round(fps) + " FPS";
        try (Font font = fonts.fontFor(14f, s, true)) {
            float tw = font.measureTextWidth(s);
            float pad = 6f, bw = tw + 2 * pad, bh = 22f, x = w - bw - 8f, y = 8f;
            Paint p = paint();
            p.setShader(null);
            p.setMode(PaintMode.FILL);
            p.setColor(0xCC000000);
            canvas.drawRRect(RRect.makeXYWH(x, y, bw, bh, 6f), p);
            p.setColor(0xFF00E676);
            canvas.drawString(s, x + pad, y + 16f, font, p);
        }
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
        // Invisible subtrees are still laid out (Qt computes geometry regardless of
        // visibility) -- only painting is skipped. A SideSheet that parks its panel at
        // `x: parent.width` while hidden needs parent.width resolved, else it parks at 0
        // and the first open slides in from the left.
        if (node == null) return;
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
            // A `visible:` binding can evaluate to undefined (null here); treat it as the
            // default (visible) rather than NPE on the unboxed boolean.
            if (!c.isVisible()) continue;
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
        if (!node.isVisible()) return;
        drawForced(canvas, node, inheritedAlpha);
    }

    // Draw a node ignoring its own `visible` flag (used to render a MultiEffect
    // source, which is normally an invisible sibling rendered only via the effect).
    void drawForced(Canvas canvas, Item node, float inheritedAlpha) {
        if (!skipLayout) {
            node.measure(text);
            followImplicitSize(node);
            applyAnchors(node);
            if (node instanceof Loader) {
                resolveLoader((Loader) node);
            }
            runLayout(node);
        }
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
            applyTransform(canvas, node, w, h, rot, sc);
            if (layerPaint != null) {
                float m = layerEffectMargin(node);
                canvas.saveLayer(Rect.makeXYWH(-m, -m, w + 2 * m, h + 2 * m), layerPaint);
            }
            // A layer.effect mask rounds the item by rendering its content into an offscreen
            // and erasing the corners with an antialiased path -- a drawn AA shape stays smooth
            // on the GPU backend, where an antialiased rounded clip can fall back to hard edges.
            float[] maskR = maskClipRadii(node);
            if (maskR != null) {
                canvas.saveLayer(Rect.makeXYWH(0, 0, w, h), null);
            } else if (clip) {
                canvas.clipRect(Rect.makeXYWH(0, 0, w, h));
            }
            // Children with z < 0 render BEHIND the node's own content (Qt); the rest on top.
            List<Item> ordered = zOrdered(node.children);
            for (Item child : ordered) {
                if (child.z.peekFloat() < 0f) draw(canvas, child, alpha);
            }
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
                    for (Item child : ordered) {
                        if (child.z.peekFloat() >= 0f) draw(canvas, child, alpha);
                    }
                } finally {
                    canvas.restoreToCount(contentSave);
                }
                drawChrome(canvas, win, w, h, alpha);
            } else {
                for (Item child : ordered) {
                    if (child.z.peekFloat() >= 0f) draw(canvas, child, alpha);
                }
            }
            if (maskR != null) eraseOutsideRoundRect(canvas, w, h, maskR);
        } finally {
            canvas.restoreToCount(savedCount);
            if (layerPaint != null) layerPaint.close();
        }
    }

    // Clear the four corners outside the rounded rect with an antialiased path, so the
    // offscreen layer composites back with smooth rounded edges (the layer.effect mask).
    private void eraseOutsideRoundRect(Canvas canvas, float w, float h, float[] r) {
        try (Path bounds = Path.makeRect(Rect.makeXYWH(0, 0, w, h));
             Path rounded = Path.makeRRect(RRect.makeComplexXYWH(0, 0, w, h, r));
             Path corners = Path.makeCombining(bounds, rounded, PathOp.DIFFERENCE);
             Paint clear = new Paint().setBlendMode(BlendMode.CLEAR).setAntiAlias(true)) {
            canvas.drawPath(corners, clear);
        }
    }

    private static void applyTransform(Canvas canvas, Item node, float w, float h, float rot, float sc) {
        if (rot != 0f || sc != 1f) {
            // Item.TransformOrigin: 0..8 row-major (TopLeft..BottomRight); col=origin%3,
            // row=origin/3 each map 0/1/2 -> start/center/end. Default 4 = Center.
            int origin = node.transformOrigin.peek().intValue();
            float px = pivot(origin % 3, w);
            float py = pivot(origin / 3, h);
            canvas.translate(px, py);
            if (rot != 0f) canvas.rotate(rot);
            if (sc != 1f) canvas.scale(sc, sc);
            canvas.translate(-px, -py);
        }
        if (!node.transform.isEmpty()) applyTransformList(canvas, node);
    }

    // Item.transform: a list of Translate/Rotation/Scale applied in order. A Rotation about
    // the x/y axis is a 3D flip; the 2D renderer approximates it by foreshortening along the
    // perpendicular axis (cos angle), which collapses the item edge-on like the real flip.
    private static void applyTransformList(Canvas canvas, Item node) {
        for (Transform t : node.transform) {
            if (t instanceof Translate) {
                Translate tr = (Translate) t;
                canvas.translate(tr.x.peekFloat(), tr.y.peekFloat());
            } else if (t instanceof Scale) {
                Scale s = (Scale) t;
                float ox = s.origin.x.peekFloat();
                float oy = s.origin.y.peekFloat();
                canvas.translate(ox, oy);
                canvas.scale(s.xScale.peekFloat(), s.yScale.peekFloat());
                canvas.translate(-ox, -oy);
            } else if (t instanceof Rotation) {
                applyRotation(canvas, (Rotation) t);
            }
        }
    }

    private static void applyRotation(Canvas canvas, Rotation r) {
        float angle = r.angle.peekFloat();
        if (angle == 0f) return;
        float ox = r.origin.x.peekFloat();
        float oy = r.origin.y.peekFloat();
        float ax = r.axis.x.peekFloat();
        float ay = r.axis.y.peekFloat();
        float az = r.axis.z.peekFloat();
        canvas.translate(ox, oy);
        if (ax == 0f && ay == 0f) {
            canvas.rotate(angle);
        } else {
            double c = Math.abs(Math.cos(Math.toRadians(angle)));
            if (ay != 0f) canvas.scale((float) c, 1f); // flip about the vertical axis
            else canvas.scale(1f, (float) c);          // flip about the horizontal axis
        }
        if (az != 0f && (ax != 0f || ay != 0f)) canvas.rotate(angle * az);
        canvas.translate(-ox, -oy);
    }

    private static float pivot(int axis, float extent) {
        return axis == 0 ? 0f : axis == 1 ? extent / 2f : extent;
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

        // Anchors live in the parent's coordinate space: filling/centering on a SIBLING
        // (not the parent) coincides with that sibling's box, so offset by its x/y. The
        // parent sits at the origin, so its offset is 0. Without this a Ripple
        // `anchors.fill: indicator` ignored the centred pill's x and sat at the left.
        Item parent = node.parent.peek();
        Item fill = a.fill.peek();
        if (fill != null) {
            float fx = fill == parent ? 0f : fill.x.peekFloat();
            float fy = fill == parent ? 0f : fill.y.peekFloat();
            node.x.set(fx + lm);
            node.y.set(fy + tm);
            node.width.set(fill.width.peekFloat() - lm - rm);
            node.height.set(fill.height.peekFloat() - tm - bm);
            return;
        }
        Item ci = a.centerIn.peek();
        if (ci != null) {
            float w = node.width.peekFloat();
            float h = node.height.peekFloat();
            float cx = ci == parent ? 0f : ci.x.peekFloat();
            float cy = ci == parent ? 0f : ci.y.peekFloat();
            node.x.set(cx + (ci.width.peekFloat() - w) / 2f);
            node.y.set(cy + (ci.height.peekFloat() - h) / 2f);
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
        // An inactive Loader holds no item (Qt frees it). Unload so an off-screen tab's
        // content -- and its animations -- stops, instead of running every frame and keeping
        // the whole scene dirty. Reactivating reloads (loadedSource/Component cleared).
        if (!Boolean.TRUE.equals(node.active.peek())) {
            if (node.loadedItem != null) clearLoadedItem(node);
            node.loadedComponent = null;
            node.loadedSource = null;
            return;
        }
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
            // The loaded file's own directory is the base for its relative imports.
            int slash = src.lastIndexOf('/');
            child = factory.create(qml, slash < 0 ? "" : src.substring(0, slash));
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
        child.initStateBindingsTree();
        // Qt fires Loader.onLoaded once the item exists; the MD3 app hangs its page
        // enter animation (slide-up + fade-in) off it. Emit after item/state are set
        // so the handler sees a fully-formed item.
        node.loaded.emit();
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

    // A layer.effect MultiEffect with maskEnabled rounds the item to its mask's shape
    // (the carousel masks its image to the card's corner radius this way). Returns the
    // four corner radii (tl,tr,br,bl) for an antialiased rounded clip, or null when
    // there is no rounded mask.
    private static float[] maskClipRadii(Item node) {
        if (!Boolean.TRUE.equals(node.layer.enabled.peek())) return null;
        Object effect = node.layer.effect.peek();
        if (!(effect instanceof MultiEffect)) return null;
        MultiEffect me = (MultiEffect) effect;
        if (!Boolean.TRUE.equals(me.maskEnabled.peek())) return null;
        Rectangle mr = firstRectangle(me.maskSource.peek());
        if (mr == null) return null;
        float tl = mr.cornerRadius(mr.topLeftRadius.peekFloat());
        float tr = mr.cornerRadius(mr.topRightRadius.peekFloat());
        float br = mr.cornerRadius(mr.bottomRightRadius.peekFloat());
        float bl = mr.cornerRadius(mr.bottomLeftRadius.peekFloat());
        if (tl <= 0 && tr <= 0 && br <= 0 && bl <= 0) return null;
        return new float[]{tl, tr, br, bl};
    }

    private static Rectangle firstRectangle(Object maskSource) {
        if (!(maskSource instanceof Item)) return null;
        if (maskSource instanceof Rectangle) return (Rectangle) maskSource;
        for (Item n : ((Item) maskSource).children) {
            Rectangle r = firstRectangle(n);
            if (r != null) return r;
        }
        return null;
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
        } else if (effect instanceof MultiEffect && Boolean.TRUE.equals(((MultiEffect) effect).shadowEnabled.peek())) {
            // A MultiEffect used as layer.effect for its drop shadow (MD3 SideSheet casts
            // a shadow to its left over the page). Same shadow knobs as drawMultiEffect.
            MultiEffect me = (MultiEffect) effect;
            int sc = applyAlpha(parseColor(me.shadowColor.peek()), (float) me.shadowOpacity.peekDouble());
            float sg = sigma(me.shadowBlur.peekFloat() * 32f); // Qt blur is 0..1
            p.setImageFilter(ImageFilter.makeDropShadow(
                me.shadowHorizontalOffset.peekFloat(), me.shadowVerticalOffset.peekFloat(), sg, sg, sc));
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
        if (effect instanceof MultiEffect && Boolean.TRUE.equals(((MultiEffect) effect).shadowEnabled.peek())) {
            MultiEffect me = (MultiEffect) effect;
            float r = me.shadowBlur.peekFloat() * 32f;
            float ox = Math.abs(me.shadowHorizontalOffset.peekFloat());
            float oy = Math.abs(me.shadowVerticalOffset.peekFloat());
            return r + Math.max(ox, oy) + 4f;
        }
        return 0f;
    }
}
