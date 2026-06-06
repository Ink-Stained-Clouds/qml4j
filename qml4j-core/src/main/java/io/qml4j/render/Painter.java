package io.qml4j.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
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
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import io.qml4j.render.items.core.Gradient;
import io.qml4j.render.items.core.GradientStop;
import io.qml4j.render.items.core.Image;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.Rectangle;
import io.qml4j.render.items.core.Text;
import io.qml4j.render.items.core.TextWrap;
import io.qml4j.render.items.effect.MultiEffect;
import io.qml4j.render.items.shape.ImageFill;
import io.qml4j.render.items.shape.PathArc;
import io.qml4j.render.items.shape.PathCubic;
import io.qml4j.render.items.shape.PathElement;
import io.qml4j.render.items.shape.PathLine;
import io.qml4j.render.items.shape.PathMove;
import io.qml4j.render.items.shape.PathQuad;
import io.qml4j.render.items.shape.Shape;
import io.qml4j.render.items.shape.ShapePath;

import java.util.List;

// The drawing surface handed to Item.paint(): skija-backed primitives keyed by
// geometry and colour, so item subclasses can render themselves without ever
// importing skija. The current Canvas is bound once per frame by the Renderer.
public final class Painter {

    private final Renderer renderer;
    private Canvas canvas;

    Painter(Renderer renderer) {
        this.renderer = renderer;
    }

    void bind(Canvas canvas) {
        this.canvas = canvas;
    }

    public int alphaColor(String color, float alpha) {
        return Renderer.applyAlpha(Renderer.parseColor(color), alpha);
    }

    public String iconGlyphFor(Text t) {
        return renderer.icons().iconGlyph(t);
    }

    public String displayTextFor(Text t) {
        return renderer.icons().displayText(t);
    }

    // A single icon glyph, vertically centred in the box via real font metrics.
    public void drawIconGlyph(String glyph, float boxH, int argb, float size) {
        try (Font f = new Font(renderer.fonts().iconTypeface(), size)) {
            FontMetrics fm = f.getMetrics();
            float baseline = boxH / 2f - (fm.getAscent() + fm.getDescent()) / 2f;
            Paint p = renderer.paint();
            p.setMode(PaintMode.FILL);
            p.setShader(null);
            p.setColor(argb);
            canvas.drawString(glyph, 0, baseline, f, p);
        }
    }

    // Multi-line text: optional wrap to boxW, optional right-elision, from y=0.
    public void drawWrappedText(String s, float boxW, int argb, float size,
                                int wrapModeEnum, boolean elideRight) {
        String wrapMode = TextLayout.wrapModeString(wrapModeEnum);
        try (Font font = renderer.fonts().fontFor(size, s)) {
            String[] lines = (wrapMode != null && boxW > 0f)
                ? TextWrap.wrap(s, wrapMode, boxW, seg -> font.measureTextWidth(seg))
                      .lines.toArray(new String[0])
                : TextLayout.splitLines(s);
            float lineH = TextLayout.lineHeight(font);
            float baseline0 = TextLayout.baselineInLine(font);
            Paint p = renderer.paint();
            p.setMode(PaintMode.FILL);
            p.setShader(null);
            p.setColor(argb);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                String line = elideRight ? TextLayout.elideToWidth(lines[i], font, boxW) : lines[i];
                canvas.drawString(line, 0, baseline0 + i * lineH, font, p);
            }
        }
    }

    // A single line of text, horizontally centred and baseline-centred in the box.
    public void drawCenteredText(String s, float boxW, float boxH, int argb, float size) {
        try (Font font = renderer.fonts().fontFor(size, s)) {
            float tw = font.measureTextWidth(s);
            float tx = (boxW - tw) / 2f;
            float ty = TextLayout.centeredBaseline(font, boxH);
            Paint p = renderer.paint();
            p.setMode(PaintMode.FILL);
            p.setShader(null);
            p.setColor(argb);
            canvas.drawString(s, tx, ty, font, p);
        }
    }

    public void fillRect(float x, float y, float w, float h, int argb) {
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setShader(null);
        p.setColor(argb);
        canvas.drawRect(Rect.makeXYWH(x, y, w, h), p);
    }

    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setShader(null);
        p.setColor(argb);
        if (radius > 0f) {
            canvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius), p);
        } else {
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), p);
        }
    }

    public void fillGradientRoundRect(float x, float y, float w, float h, float radius,
                                      Gradient gradient, float alpha) {
        Shader shader = buildLinearGradient(gradient, w, h);
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setShader(shader);
        p.setColor(Renderer.applyAlpha(0xFFFFFFFF, alpha));
        if (radius > 0f) {
            canvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius), p);
        } else {
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), p);
        }
        p.setShader(null);
        if (shader != null) shader.close();
    }

    public void strokeRoundRect(float x, float y, float w, float h, float radius,
                                int argb, float strokeWidth) {
        Paint p = renderer.paint();
        p.setMode(PaintMode.STROKE);
        p.setStrokeWidth(strokeWidth);
        p.setShader(null);
        p.setColor(argb);
        if (radius > 0f) {
            canvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius), p);
        } else {
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), p);
        }
        p.setMode(PaintMode.FILL);
    }

    private static Shader buildLinearGradient(Gradient g, float w, float h) {
        List<GradientStop> stops = g.stops;
        int n = stops.size();
        if (n == 0) return null;
        int[] colors = new int[n];
        float[] positions = new float[n];
        for (int i = 0; i < n; i++) {
            GradientStop s = stops.get(i);
            colors[i] = Renderer.parseColor(s.color.peek());
            positions[i] = s.position.peek().floatValue();
        }
        return Shader.makeLinearGradient(0, 0, 0, h, colors, positions);
    }

    // Image is its own subsystem: source loading/decoding, intrinsic-size probe,
    // ImageFill plan, and the draw/tile blit. It lives here (not on the Image item)
    // because it touches skija decoding and the resource loader.
    public void drawImage(Image node, float w, float h) {
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
            ResourceLoader resources = renderer.resources();
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
                drawImagePlan(node.skiaImage, plan);
                break;
            case TILE_X:
            case TILE_Y:
            case TILE_XY:
                drawTilePlan(node.skiaImage, plan, w, h);
                break;
        }
    }

    private void drawImagePlan(io.github.humbleui.skija.Image img, ImageFill.Plan plan) {
        Rect src = Rect.makeXYWH(plan.srcX, plan.srcY, plan.srcW, plan.srcH);
        Rect dst = Rect.makeXYWH(plan.dstX, plan.dstY, plan.dstW, plan.dstH);
        canvas.drawImageRect(img, src, dst);
    }

    private void drawTilePlan(io.github.humbleui.skija.Image img,
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

    public void drawShape(Shape shape, float alpha) {
        try (Paint p = new Paint()) {
            p.setAntiAlias(true);
            for (ShapePath sp : shape.elements) {
                try (Path path = buildPath(sp)) {
                    fillPath(path, sp, alpha, p);
                    strokePath(path, sp, alpha, p);
                }
            }
        }
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

    private void fillPath(Path path, ShapePath sp, float alpha, Paint p) {
        int argb = shapeArgb(sp.fillColor.peek(), alpha);
        if (argb == 0) return;
        p.setColor(argb);
        p.setMode(PaintMode.FILL);
        canvas.drawPath(path, p);
    }

    private void strokePath(Path path, ShapePath sp, float alpha, Paint p) {
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
        int rgb = Renderer.parseColor(colorStr);
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

    // v0 MultiEffect: paint the source subtree clipped to the mask's rounded-rect
    // shape (true per-pixel alpha masking is not implemented). The source is
    // normally an invisible sibling, so we draw it through the renderer here.
    public void drawMultiEffect(MultiEffect me, float w, float h, float alpha) {
        Object src = me.source.peek();
        if (!(src instanceof Item)) return;
        Item source = (Item) src;

        // Drop shadow: render the source through a drop-shadow image filter.
        if (Boolean.TRUE.equals(me.shadowEnabled.peek())) {
            float op = (float) (alpha * me.shadowOpacity.peek().doubleValue());
            int sc = Renderer.applyAlpha(Renderer.parseColor(me.shadowColor.peek()), op);
            float dy = me.shadowVerticalOffset.peek().floatValue();
            float dx = me.shadowHorizontalOffset.peek().floatValue();
            float sg = Renderer.sigma(me.shadowBlur.peek().floatValue() * 32f); // Qt blur is 0..1
            Paint sp = new Paint();
            sp.setImageFilter(ImageFilter.makeDropShadow(dx, dy, sg, sg, sc));
            float mg = sg * 3f + Math.abs(dx) + Math.abs(dy) + 8f;
            int save = canvas.saveLayer(Rect.makeXYWH(-mg, -mg, w + 2 * mg, h + 2 * mg), sp);
            try { renderer.drawForced(canvas, source, alpha); }
            finally { canvas.restoreToCount(save); sp.close(); }
            return;
        }

        // Mask: clip the source to the mask's rounded-rect shape (v0 approximation).
        int save = canvas.save();
        if (Boolean.TRUE.equals(me.maskEnabled.peek())) {
            Rectangle mr = maskRect(me.maskSource.peek());
            float tl = mr == null ? 0f : mr.cornerRadius(mr.topLeftRadius.peek().floatValue());
            float tr = mr == null ? 0f : mr.cornerRadius(mr.topRightRadius.peek().floatValue());
            float br = mr == null ? 0f : mr.cornerRadius(mr.bottomRightRadius.peek().floatValue());
            float bl = mr == null ? 0f : mr.cornerRadius(mr.bottomLeftRadius.peek().floatValue());
            if (tl > 0 || tr > 0 || br > 0 || bl > 0) {
                // Per-corner: a first/last SegmentedButton segment is round on one side,
                // square on the other -- a single radius clipped the ripple as a rect.
                canvas.clipRRect(RRect.makeComplexXYWH(0, 0, w, h, new float[]{tl, tr, br, bl}));
            } else {
                canvas.clipRect(Rect.makeXYWH(0, 0, w, h));
            }
        }
        try { renderer.drawForced(canvas, source, alpha); }
        finally { canvas.restoreToCount(save); }
    }

    // The first Rectangle in the mask subtree -- its effective per-corner radii
    // define the clip shape.
    private static Rectangle maskRect(Object maskSource) {
        if (!(maskSource instanceof Item)) return null;
        if (maskSource instanceof Rectangle) return (Rectangle) maskSource;
        for (Item n : ((Item) maskSource).children) {
            Rectangle r = maskRect(n);
            if (r != null) return r;
        }
        return null;
    }
}
