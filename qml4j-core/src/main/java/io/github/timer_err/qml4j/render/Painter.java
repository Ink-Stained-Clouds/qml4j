package io.github.timer_err.qml4j.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.FilterMipmap;
import io.github.humbleui.skija.FilterMode;
import io.github.humbleui.skija.MipmapMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PathDirection;
import io.github.humbleui.skija.PathEllipseArc;
import io.github.humbleui.skija.PathFillMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.TextLine;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import io.github.timer_err.qml4j.render.items.core.Gradient;
import io.github.timer_err.qml4j.render.items.core.GradientStop;
import io.github.timer_err.qml4j.render.items.core.Image;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
import io.github.timer_err.qml4j.render.items.core.Text;
import io.github.timer_err.qml4j.render.items.core.TextWrap;
import io.github.timer_err.qml4j.render.items.effect.MultiEffect;
import io.github.timer_err.qml4j.render.items.input.TextEdit;
import io.github.timer_err.qml4j.render.items.input.TextField;
import io.github.timer_err.qml4j.render.items.input.TextInput;
import io.github.timer_err.qml4j.render.items.shape.ImageFill;
import io.github.timer_err.qml4j.render.items.shape.PathArc;
import io.github.timer_err.qml4j.render.items.shape.PathCubic;
import io.github.timer_err.qml4j.render.items.shape.PathElement;
import io.github.timer_err.qml4j.render.items.shape.PathLine;
import io.github.timer_err.qml4j.render.items.shape.PathMove;
import io.github.timer_err.qml4j.render.items.shape.PathQuad;
import io.github.timer_err.qml4j.render.items.shape.Shape;
import io.github.timer_err.qml4j.render.items.shape.ShapePath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // A 2D drawing context bound to the current canvas (already translated to the
    // painting item's origin), for a Canvas item's onPaint handler.
    public Context2D context2D() {
        return new Context2D(canvas, renderer);
    }

    // Paint a Canvas item's onPaint into an offscreen layer (the item rect) and composite
    // it back at `alpha`. The layer is what makes clearRect (BlendMode.CLEAR) erase only
    // the canvas instead of punching a transparent hole through the scene to the
    // framebuffer -- which shows as black on a GL backend.
    public void inLayer(float w, float h, float alpha, Runnable body) {
        int save = canvas.saveLayerAlpha(Rect.makeXYWH(0, 0, Math.max(0f, w), Math.max(0f, h)), Math.round(alpha * 255));
        try {
            body.run();
        } finally {
            canvas.restoreToCount(save);
        }
    }

    // A QtQuick Canvas: run onPaint into an offscreen backing surface only when dirty
    // (requestPaint / resize), then blit the cached image every frame. Without this the
    // onPaint handler -- particle sims, gradients -- re-runs every render frame regardless
    // of requestPaint, far above the widget's intended fps. (The QML Canvas item is FQN'd:
    // its simple name clashes with the imported skija Canvas this Painter draws through.)
    // GPU-backed offscreen when rendering to a GPU surface (so the blit is GPU->GPU and
    // composites every frame); raster when headless. Falls back to raster if the GPU
    // surface can't be created.
    private Surface makeBackingSurface(int w, int h) {
        io.github.humbleui.skija.DirectContext ctx = renderer.gpuContext();
        if (ctx != null) {
            // Match the GL window surface's BOTTOM_LEFT origin: a TOP_LEFT offscreen blitted
            // onto a BOTTOM_LEFT canvas is vertically flipped, displacing small transformed
            // content (a centred, rotated loading spinner) off its own bounds -- it vanished.
            Surface gpu = Surface.makeRenderTarget(ctx, true,
                io.github.humbleui.skija.ImageInfo.makeN32Premul(w, h), 0,
                io.github.humbleui.skija.SurfaceOrigin.BOTTOM_LEFT, null);
            if (gpu != null) return gpu;
        }
        return Surface.makeRasterN32Premul(w, h);
    }

    // Cache onPaint into an offscreen, repainting only when dirty (big win for animated/
    // static canvases). -Dqml4j.canvasCache=false falls back to per-frame direct draw.
    private static final boolean CANVAS_CACHE = !"false".equals(System.getProperty("qml4j.canvasCache", "true"));

    // Snap a device scale to 0.5 steps (min 1) so transient scale animations don't
    // resize the canvas backing every frame.
    private static float quantizeScale(float s) {
        if (s < 0.01f) return 1f;
        return Math.max(1f, Math.round(s * 2f) / 2f);
    }

    public void paintCanvas(io.github.timer_err.qml4j.render.items.core.Canvas node, float w, float h, float alpha) {
        if (!CANVAS_CACHE) {
            node.bindContext(context2D());
            try {
                inLayer(w, h, alpha, node.paint::emit);
            } finally {
                node.bindContext(null);
            }
            return;
        }
        // Back the canvas at DEVICE resolution: the main canvas carries the host
        // uiScale, so a logical-sized backing (w x h) gets upsampled blurry on
        // blit. Size the offscreen by the live logical->device scale and render
        // onPaint into it scaled to match, then blit it back 1:1 in device px.
        float[] m = canvas.getLocalToDevice().getMat();
        float sx = m[0], sy = m[5], tx = m[3], ty = m[7];
        // Quantise the backing scale (snap to 0.5 steps) so a transient item-scale
        // animation (a dialog popping 0.9->1.0) doesn't change the offscreen size
        // every frame -- that would recreate + repaint the backing each frame and
        // stutter the animation. The blit below still uses the live matrix scale,
        // so the result stays correctly sized; only the cached resolution is
        // pinned. Residual scale just resamples the (already device-res) cache.
        float dsx = quantizeScale(Math.abs(sx));
        float dsy = quantizeScale(Math.abs(sy));
        int iw = Math.max(1, Math.round(w * dsx));
        int ih = Math.max(1, Math.round(h * dsy));
        if (node.backing == null || node.backingW != iw || node.backingH != ih) {
            if (node.backing != null) node.backing.close();
            node.backing = makeBackingSurface(iw, ih);
            node.backingW = iw;
            node.backingH = ih;
            node.dirty = true;
        }
        if (node.dirty) {
            io.github.humbleui.skija.Canvas bc = node.backing.getCanvas();
            // The backing canvas is persistent; ctx.translate/rotate mutate its matrix and
            // ctx.reset() does NOT restore it (the direct path got a fresh per-frame matrix
            // from the Renderer's save/restore). Save/restore here so each onPaint starts from
            // identity -- else a rotating canvas (the loading spinner) drifts off-surface and
            // blanks after a few frames.
            int sv = bc.save();
            bc.clear(0x00000000);
            // onPaint draws in logical coords (0..w, 0..h); scale up to fill the
            // device-resolution backing so the result is crisp.
            bc.scale(dsx, dsy);
            node.bindContext(new Context2D(bc, renderer));
            try {
                node.paint.emit();
            } finally {
                node.bindContext(null);
                bc.restoreToCount(sv);
            }
            node.dirty = false;
        }
        // Surface.draw blits the backing onto the main canvas (the matrix already carries
        // this item's scale/position) without the makeImageSnapshot/close churn -- closing a
        // just-snapshotted raster Image while its blit is still queued on the GPU backend
        // blanked the canvas a frame after it appeared.
        Paint p = renderer.paint();
        p.setShader(null);
        p.setMode(PaintMode.FILL);
        p.setColor(Renderer.applyAlpha(0xFFFFFFFF, alpha));
        // Snap the blit to an integer device pixel (a canvas centred at a fractional
        // device position would resample blurry), then scale the device-res backing
        // back down so its pixels land 1:1 on device pixels.
        int save = canvas.save();
        if (sx != 0 && sy != 0) {
            canvas.translate((Math.round(tx) - tx) / sx, (Math.round(ty) - ty) / sy);
        }
        canvas.scale(1f / dsx, 1f / dsy);
        node.backing.draw(canvas, 0, 0, p);
        canvas.restoreToCount(save);
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

    // A single Material Symbols icon, shaped from its ligature name (so the font's GSUB
    // forms the glyph) and vertically centred in the box via real font metrics.
    // Shaping a Material Symbols ligature (name -> glyph via the font's GSUB) is expensive
    // and re-runs every frame for every icon; cache the shaped TextLine (and per-size Font)
    // keyed on (name, size). Native handles are long-lived and bounded by the app's distinct
    // icon/size set, so they are intentionally not closed.
    private final Map<Float, Font> iconFonts = new HashMap<>();
    private final Map<String, TextLine> iconLines = new HashMap<>();

    // Shaping a string (HarfBuzz via Skia) on every drawString/measureTextWidth
    // re-runs for every visible label every frame -- the dominant paint cost. Cache
    // the shaped TextLine per (font, text) and draw/measure through it. Bounded LRU
    // (strings are unbounded), closing evicted native handles.
    private final Map<String, TextLine> textLines =
        new java.util.LinkedHashMap<String, TextLine>(512, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TextLine> e) {
                if (size() > 600) {
                    e.getValue().close();
                    return true;
                }
                return false;
            }
        };

    private TextLine textLine(Font font, String s) {
        return textLines.computeIfAbsent(System.identityHashCode(font) + " " + s,
            k -> TextLine.make(s, font));
    }

    private float textWidth(Font font, String s) {
        return textLine(font, s).getWidth();
    }

    // Elision (…) recomputed for every label every frame measured each substring
    // via uncached shaping -- a real per-frame draw cost on text-heavy screens.
    // Cache the elided result per (font, width, text).
    private final Map<String, String> elideCache =
        new java.util.LinkedHashMap<String, String>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> e) {
                return size() > 600;
            }
        };

    // Wrapping (TextWrap.wrap) re-shapes every segment every frame for every
    // wrapped label -- a big cost for large bodies. Cache the wrapped lines per
    // (font, mode, width, text).
    private final Map<String, String[]> wrapCache =
        new java.util.LinkedHashMap<String, String[]>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String[]> e) {
                return size() > 256;
            }
        };

    private String[] wrapLines(Font font, String s, String mode, float boxW) {
        String key = System.identityHashCode(font) + "|" + mode + "|" + Math.round(boxW) + "|" + s;
        String[] hit = wrapCache.get(key);
        if (hit != null) return hit;
        String[] lines = TextWrap.wrap(s, mode, boxW, seg -> textWidth(font, seg))
                .lines.toArray(new String[0]);
        wrapCache.put(key, lines);
        return lines;
    }

    private String elideRightToWidth(Font font, String line, float boxW) {
        if (boxW <= 0f) return line;
        String key = System.identityHashCode(font) + "|" + Math.round(boxW) + "|" + line;
        String hit = elideCache.get(key);
        if (hit != null) return hit;
        String result;
        if (textWidth(font, line) <= boxW) {
            result = line;
        } else {
            float ellW = textWidth(font, "…");
            int end = line.length();
            while (end > 0 && textWidth(font, line.substring(0, end)) + ellW > boxW) end--;
            result = line.substring(0, end) + "…";
        }
        elideCache.put(key, result);
        return result;
    }

    public void drawIconGlyph(String name, float boxH, int argb, float size) {
        Font f = iconFonts.computeIfAbsent(size, sz -> new Font(renderer.fonts().iconTypeface(), sz));
        TextLine line = iconLines.computeIfAbsent(name + '\0' + size, k -> TextLine.make(name, f));
        FontMetrics fm = f.getMetrics();
        float baseline = boxH / 2f - (fm.getAscent() + fm.getDescent()) / 2f;
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setShader(null);
        p.setColor(argb);
        canvas.drawTextLine(line, 0, baseline, p);
    }

    // Multi-line text: optional wrap to boxW, optional right-elision, from y=0. Each line
    // is offset by hAlign (Text.AlignHCenter/AlignRight) within boxW, so a centred Text
    // centres every wrapped line, not just the block.
    public void drawWrappedText(String s, float boxW, int argb, float size,
                                int wrapModeEnum, boolean elideRight, boolean bold, int hAlign) {
        String wrapMode = TextLayout.wrapModeString(wrapModeEnum);
        { Font font = renderer.fonts().fontFor(size, s, bold);
            String[] lines = (wrapMode != null && boxW > 0f)
                ? wrapLines(font, s, wrapMode, boxW)
                : TextLayout.splitLines(s);
            float lineH = TextLayout.lineHeight(font);
            float baseline0 = TextLayout.baselineInLine(font);
            Paint p = renderer.paint();
            p.setMode(PaintMode.FILL);
            p.setShader(null);
            p.setColor(argb);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                String line = elideRight ? elideRightToWidth(font, lines[i], boxW) : lines[i];
                float tx = lineOffset(line, font, boxW, hAlign);
                canvas.drawTextLine(textLine(font, line), tx, baseline0 + i * lineH, p);
            }
        }
    }

    // The x offset placing a line within boxW per its horizontal alignment.
    // AlignHCenter (4) centres, AlignRight (2) right-aligns; AlignLeft/justify stay at 0.
    private float lineOffset(String line, Font font, float boxW, int hAlign) {
        if (boxW <= 0f || hAlign == 1) return 0f;
        if (hAlign == 4) return (boxW - textWidth(font, line)) / 2f;
        if (hAlign == 2) return boxW - textWidth(font, line);
        return 0f;
    }

    // A single line of text, horizontally centred and baseline-centred in the box.
    public void drawCenteredText(String s, float boxW, float boxH, int argb, float size) {
        { Font font = renderer.fonts().fontFor(size, s);
            float tw = textWidth(font, s);
            float tx = (boxW - tw) / 2f;
            float ty = TextLayout.centeredBaseline(font, boxH);
            Paint p = renderer.paint();
            p.setMode(PaintMode.FILL);
            p.setShader(null);
            p.setColor(argb);
            canvas.drawTextLine(textLine(font, s), tx, ty, p);
        }
    }

    public void fillRect(float x, float y, float w, float h, int argb) {
        if (w <= 0f || h <= 0f) return;
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setShader(null);
        p.setColor(argb);
        canvas.drawRect(Rect.makeXYWH(x, y, w, h), p);
    }

    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0f || h <= 0f) return;
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
        if (w <= 0f || h <= 0f) return;
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
        if (w <= 0f || h <= 0f) return;
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
            positions[i] = s.position.peekFloat();
        }
        boolean horizontal = g.orientation.peekDouble() == 1;
        return horizontal
            ? Shader.makeLinearGradient(0, 0, w, 0, colors, positions)
            : Shader.makeLinearGradient(0, 0, 0, h, colors, positions);
    }

    // Image is its own subsystem: source loading/decoding, intrinsic-size probe,
    // ImageFill plan, and the draw/tile blit. It lives here (not on the Image item)
    // because it touches skija decoding and the resource loader. skija's Image is
    // FQN'd throughout this section: its simple name clashes with the QML Image item.
    // Image.fillMode may be the string form or the Image.* enum (a Long); map to the
    // string ImageFill understands.
    private static String fillModeString(Object o) {
        if (o instanceof String) return (String) o;
        if (o instanceof Number) {
            switch (((Number) o).intValue()) {
                case 1: return "PreserveAspectFit";
                case 2: return "PreserveAspectCrop";
                case 3: return "Tile";
                case 4: return "TileVertically";
                case 5: return "TileHorizontally";
                case 6: return "Pad";
                default: return "Stretch";
            }
        }
        return "Stretch";
    }

    // Decode encoded image bytes into the node's Skija image (render thread only) and set
    // status Ready/Error. bytes == null -> Error.
    private void decodeInto(Image node, byte[] bytes) {
        if (bytes != null) {
            int[] dim = peekImageDimensions(bytes);
            node.intrinsicWidth = dim[0];
            node.intrinsicHeight = dim[1];
            try {
                io.github.humbleui.skija.Image full = io.github.humbleui.skija.Image.makeFromEncoded(bytes);
                node.skiaImage = downscaleToSourceSize(node, full);
            } catch (Throwable t) {
                node.skiaImage = null;
            }
        }
        node.status.set(node.skiaImage != null ? 1 : 3);
    }

    // Honour Image.sourceSize like Qt: decode-then-shrink to roughly the on-screen size so
    // a multi-megapixel photo isn't sampled down by 4x+ every frame (which reads as blurry).
    // Shrink by the LARGER ratio so both dimensions stay >= the target -- a PreserveAspectCrop
    // image still has enough pixels to cover its box without upscaling (which would re-blur).
    private io.github.humbleui.skija.Image downscaleToSourceSize(Image node,
                                                                io.github.humbleui.skija.Image full) {
        int iw = full.getWidth();
        int ih = full.getHeight();
        int sw = node.sourceSize.width.peek().intValue();
        int sh = node.sourceSize.height.peek().intValue();
        float f;
        if (sw > 0 && sh > 0) {
            f = Math.max((float) sw / iw, (float) sh / ih);
        } else if (sw > 0) {
            f = (float) sw / iw;
        } else if (sh > 0) {
            f = (float) sh / ih;
        } else {
            f = 1f;
        }
        if (f >= 1f) return full;
        int tw = Math.max(1, Math.round(iw * f));
        int th = Math.max(1, Math.round(ih * f));
        try (Surface surf = Surface.makeRasterN32Premul(tw, th)) {
            // Trilinear (mipmap) for the large one-time shrink: box-averaged mip levels
            // antialias the downscale cleanly, where a single bilinear pass would skip
            // source pixels and a bicubic pass would ring at edges.
            surf.getCanvas().drawImageRect(full,
                Rect.makeXYWH(0, 0, iw, ih), Rect.makeXYWH(0, 0, tw, th),
                new FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true);
            io.github.humbleui.skija.Image scaled = surf.makeImageSnapshot();
            full.close();
            node.intrinsicWidth = tw;
            node.intrinsicHeight = th;
            return scaled;
        }
    }

    public void drawImage(Image node, float w, float h) {
        String src = node.source.peek();
        if (src == null || src.isEmpty()) { node.status.set(0); return; }
        if (!src.equals(node.loadedSource)) {
            if (node.skiaImage != null) {
                node.skiaImage.close();
                node.skiaImage = null;
            }
            node.loadedSource = src;
            node.intrinsicWidth = 0;
            node.intrinsicHeight = 0;
            node.fetchedBytes = null;
            node.fetchDone = false;
            node.fetchStarted = false;
            if (ImageLoader.isRemote(src)) {
                node.status.set(2); // Loading -- decoded on the render thread once fetched
            } else {
                ResourceLoader resources = renderer.resources();
                decodeInto(node, resources != null ? resources.load(src) : null);
            }
        }
        // A remote source fetches on a background thread; consume the bytes here (on the
        // render thread) once they arrive, so the spinner stops and the image appears.
        if (ImageLoader.isRemote(src)) {
            if (!node.fetchStarted) {
                node.fetchStarted = true;
                ImageLoader.fetch(node, src);
            }
            if (node.fetchDone && node.status.peek().intValue() == 2) {
                decodeInto(node, node.fetchedBytes);
            }
        }
        if (node.skiaImage == null) return;
        int iw = node.intrinsicWidth;
        int ih = node.intrinsicHeight;
        if (iw <= 0 || ih <= 0) return;
        if (w <= 0) w = iw;
        if (h <= 0) h = ih;
        ImageFill.Plan plan = ImageFill.compute(fillModeString(node.fillMode.peek()), iw, ih, w, h);
        if (plan == null) return;
        node.paintedWidth.set(plan.paintedWidth);
        node.paintedHeight.set(plan.paintedHeight);
        float radius = node.radius.peekFloat();
        int save = radius > 0 ? canvas.save() : -1;
        if (radius > 0) canvas.clipRRect(RRect.makeXYWH(0, 0, w, h, radius), true);
        try {
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
        } finally {
            if (save >= 0) canvas.restoreToCount(save);
        }
    }

    // Bilinear for the on-screen draw: the carousel image is pre-shrunk near its display
    // size in decodeInto, so this only resolves a small residual scale. Bicubic (Mitchell)
    // overshoots at high-contrast edges -- visible ringing/jaggies -- so linear stays clean.
    private static final SamplingMode IMAGE_SAMPLING = SamplingMode.LINEAR;

    private void drawImagePlan(io.github.humbleui.skija.Image img, ImageFill.Plan plan) {
        Rect src = Rect.makeXYWH(plan.srcX, plan.srcY, plan.srcW, plan.srcH);
        Rect dst = Rect.makeXYWH(plan.dstX, plan.dstY, plan.dstW, plan.dstH);
        canvas.drawImageRect(img, src, dst, IMAGE_SAMPLING, null, true);
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
                    canvas.drawImageRect(img, src, dst, IMAGE_SAMPLING, null, true);
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
        pb.moveTo(sp.startX.peekFloat(), sp.startY.peekFloat());
        for (PathElement e : sp.pathElements) {
            appendElement(pb, e);
        }
        return pb.build();
    }

    private void appendElement(PathBuilder pb, PathElement e) {
        if (e instanceof PathLine) {
            PathLine l = (PathLine) e;
            pb.lineTo(l.x.peekFloat(), l.y.peekFloat());
        } else if (e instanceof PathMove) {
            PathMove m = (PathMove) e;
            pb.moveTo(m.x.peekFloat(), m.y.peekFloat());
        } else if (e instanceof PathQuad) {
            PathQuad q = (PathQuad) e;
            pb.quadTo(q.controlX.peekFloat(), q.controlY.peekFloat(),
                      q.x.peekFloat(), q.y.peekFloat());
        } else if (e instanceof PathCubic) {
            PathCubic c = (PathCubic) e;
            pb.cubicTo(c.control1X.peekFloat(), c.control1Y.peekFloat(),
                       c.control2X.peekFloat(), c.control2Y.peekFloat(),
                       c.x.peekFloat(), c.y.peekFloat());
        } else if (e instanceof PathArc) {
            PathArc a = (PathArc) e;
            PathEllipseArc size = Boolean.TRUE.equals(a.useLargeArc.peek())
                ? PathEllipseArc.LARGER : PathEllipseArc.SMALLER;
            PathDirection dir = "Counterclockwise".equals(a.direction.peek())
                ? PathDirection.COUNTER_CLOCKWISE : PathDirection.CLOCKWISE;
            pb.ellipticalArcTo(a.radiusX.peekFloat(), a.radiusY.peekFloat(),
                               a.xAxisRotation.peekFloat(), size, dir,
                               a.x.peekFloat(), a.y.peekFloat());
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
        float sw = sp.strokeWidth.peekFloat();
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
            float op = (float) (alpha * me.shadowOpacity.peekDouble());
            int sc = Renderer.applyAlpha(Renderer.parseColor(me.shadowColor.peek()), op);
            float dy = me.shadowVerticalOffset.peekFloat();
            float dx = me.shadowHorizontalOffset.peekFloat();
            float sg = Renderer.sigma(me.shadowBlur.peekFloat() * 32f); // Qt blur is 0..1
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
            float tl = mr == null ? 0f : mr.cornerRadius(mr.topLeftRadius.peekFloat());
            float tr = mr == null ? 0f : mr.cornerRadius(mr.topRightRadius.peekFloat());
            float br = mr == null ? 0f : mr.cornerRadius(mr.bottomRightRadius.peekFloat());
            float bl = mr == null ? 0f : mr.cornerRadius(mr.bottomLeftRadius.peekFloat());
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

    private static final long CARET_BLINK_MS = 500;

    private static boolean caretBlinkOn() {
        return (System.currentTimeMillis() / CARET_BLINK_MS) % 2 == 0;
    }

    public void drawTextField(TextField tf, float w, float h, float alpha) {
        float radius = Math.max(0f, tf.radius.peekFloat());
        Paint p = renderer.paint();
        p.setShader(null);
        p.setMode(PaintMode.FILL);
        p.setColor(Renderer.applyAlpha(Renderer.parseColor(tf.backgroundColor.peek()), alpha));
        if (radius > 0f) {
            canvas.drawRRect(RRect.makeXYWH(0, 0, w, h, radius), p);
        } else {
            canvas.drawRect(Rect.makeXYWH(0, 0, w, h), p);
        }
        float bw = Math.max(0f, tf.borderWidth.peekFloat());
        if (bw > 0f) {
            boolean focused = Boolean.TRUE.equals(tf.activeFocus.peek());
            int bc = Renderer.parseColor(focused ? tf.focusBorderColor.peek() : tf.borderColor.peek());
            p.setMode(PaintMode.STROKE);
            p.setStrokeWidth(bw);
            p.setColor(Renderer.applyAlpha(bc, alpha));
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
        float size = tf.fontSize.peekFloat();
        float pad = tf.padding.peekFloat();
        int tfSave = canvas.save();
        try {
            canvas.translate(pad, 0);
            String s = tf.text.peek();
            if (s == null || s.isEmpty()) {
                String ph = tf.placeholderText.peek();
                if (ph != null && !ph.isEmpty()) {
                    { Font font = renderer.fonts().fontFor(size, ph);
                        p.setColor(Renderer.applyAlpha(Renderer.parseColor(tf.placeholderTextColor.peek()), alpha));
                        canvas.drawTextLine(textLine(font, ph), 0, TextLayout.centeredBaseline(font, h), p);
                    }
                }
            }
            drawTextInput(tf, w, h, alpha);
        } finally {
            canvas.restoreToCount(tfSave);
        }
    }

    // The string shown for a TextInput honouring echoMode: Password masks each char with
    // passwordCharacter, NoEcho shows nothing, else the raw text.
    private static String echoDisplay(TextInput ti) {
        String raw = ti.text.peek();
        if (raw == null) raw = "";
        int mode = ti.echoMode.peekInt();
        if (mode == 1) return "";                 // NoEcho
        if (mode == 2) {                           // Password
            String pc = ti.passwordCharacter.peek();
            char c = pc == null || pc.isEmpty() ? '•' : pc.charAt(0);
            StringBuilder b = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) b.append(c);
            return b.toString();
        }
        return raw;
    }

    public void drawTextInput(TextInput ti, float w, float h, float alpha) {
        String s = echoDisplay(ti);
        if (s == null) s = "";
        float size = ti.fontSize.peekFloat();
        { Font font = renderer.fonts().fontFor(size, s);
            float baseline = TextLayout.centeredBaseline(font, h);
            float glyphTop = baseline + TextLayout.glyphTopOffset(font);
            float glyphHeight = TextLayout.glyphExtent(font);
            paintSelectionRect(ti, s, font, glyphTop, glyphHeight, alpha);
            if (!s.isEmpty()) {
                Paint p = renderer.paint();
                p.setColor(Renderer.applyAlpha(Renderer.parseColor(ti.color.peek()), alpha));
                canvas.drawTextLine(textLine(font, s), 0, baseline, p);
            }
            if (Boolean.TRUE.equals(ti.activeFocus.peek()) && caretBlinkOn()) {
                int pos = Math.max(0, Math.min(ti.cursorPosition.peekInt(), s.length()));
                float cx = textWidth(font, s.substring(0, pos));
                Paint p = renderer.paint();
                p.setMode(PaintMode.FILL);
                p.setColor(Renderer.applyAlpha(Renderer.parseColor(ti.color.peek()), alpha));
                float cw = Math.max(1f, size / 16f);
                canvas.drawRect(Rect.makeXYWH(cx, glyphTop, cw, glyphHeight), p);
            }
        }
    }

    private void paintSelectionRect(TextInput ti, String s, Font font,
                                    float glyphTop, float glyphHeight, float alpha) {
        int len = s.length();
        int selS = Math.max(0, Math.min(ti.selectionStart.peekInt(), len));
        int selE = Math.max(selS, Math.min(ti.selectionEnd.peekInt(), len));
        if (selE <= selS) return;
        float x0 = textWidth(font, s.substring(0, selS));
        float x1 = textWidth(font, s.substring(0, selE));
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setColor(Renderer.applyAlpha(Renderer.parseColor(ti.selectionColor.peek()), alpha));
        canvas.drawRect(Rect.makeXYWH(x0, glyphTop, x1 - x0, glyphHeight), p);
    }

    public void drawTextEdit(TextEdit te, float w, float h, float alpha) {
        String s = te.text.peek();
        if (s == null) s = "";
        float size = te.fontSize.peekFloat();
        { Font font = renderer.fonts().fontFor(size, s);
            TextWrap.Result wrapped = renderer.textLayout().wrapFor(te, s, w, size, font);
            te.lineCount.set(wrapped.lines.size());
            float lineH = TextLayout.lineHeight(font);
            float total = lineH * wrapped.lines.size();
            float yOffset = renderer.textLayout().topOffset(te.verticalAlignment.peek(), h, total);
            paintSelectionMultiline(te, wrapped, font, yOffset, lineH, size, alpha);
            Paint p = renderer.paint();
            p.setColor(Renderer.applyAlpha(Renderer.parseColor(te.color.peek()), alpha));
            for (int i = 0; i < wrapped.lines.size(); i++) {
                String line = wrapped.lines.get(i);
                if (!line.isEmpty()) {
                    float baseline = yOffset + i * lineH + TextLayout.baselineInLine(font);
                    canvas.drawTextLine(textLine(font, line), 0, baseline, p);
                }
            }
            if (Boolean.TRUE.equals(te.activeFocus.peek()) && caretBlinkOn()) {
                drawCaretMultiline(te, wrapped, font, yOffset, lineH, size, alpha);
            }
        }
    }

    private void paintSelectionMultiline(TextEdit te, TextWrap.Result wrapped,
                                         Font font, float yOffset, float lineH, float size, float alpha) {
        int len = te.cachedText == null ? 0 : te.cachedText.length();
        int selS = Math.max(0, Math.min(te.selectionStart.peekInt(), len));
        int selE = Math.max(selS, Math.min(te.selectionEnd.peekInt(), len));
        if (selE <= selS) return;
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setColor(Renderer.applyAlpha(Renderer.parseColor(te.selectionColor.peek()), alpha));
        float glyphTop = TextLayout.baselineInLine(font) + TextLayout.glyphTopOffset(font);
        float glyphHeight = TextLayout.glyphExtent(font);
        for (int i = 0; i < wrapped.lines.size(); i++) {
            int ls = wrapped.starts[i];
            String line = wrapped.lines.get(i);
            int le = ls + line.length();
            if (selE <= ls || selS >= le) continue;
            int a = Math.max(selS, ls) - ls;
            int b = Math.min(selE, le) - ls;
            float x0 = a == 0 ? 0 : textWidth(font, line.substring(0, a));
            float x1 = textWidth(font, line.substring(0, b));
            float y = yOffset + i * lineH + glyphTop;
            canvas.drawRect(Rect.makeXYWH(x0, y, x1 - x0, glyphHeight), p);
        }
    }

    private void drawCaretMultiline(TextEdit te, TextWrap.Result wrapped,
                                    Font font, float yOffset, float lineH, float size, float alpha) {
        int len = te.cachedText == null ? 0 : te.cachedText.length();
        int pos = Math.max(0, Math.min(te.cursorPosition.peekInt(), len));
        int lineIdx = TextWrap.lineForCaret(wrapped, pos);
        String line = wrapped.lines.get(lineIdx);
        int col = Math.max(0, Math.min(pos - wrapped.starts[lineIdx], line.length()));
        float cx = col == 0 ? 0 : textWidth(font, line.substring(0, col));
        float glyphTop = TextLayout.baselineInLine(font) + TextLayout.glyphTopOffset(font);
        float glyphHeight = TextLayout.glyphExtent(font);
        Paint p = renderer.paint();
        p.setMode(PaintMode.FILL);
        p.setColor(Renderer.applyAlpha(Renderer.parseColor(te.color.peek()), alpha));
        float cw = Math.max(1f, size / 16f);
        canvas.drawRect(Rect.makeXYWH(cx, yOffset + lineIdx * lineH + glyphTop, cw, glyphHeight), p);
    }
}
