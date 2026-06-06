package io.qml4j.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import io.qml4j.render.items.core.Gradient;
import io.qml4j.render.items.core.GradientStop;
import io.qml4j.render.items.core.Image;
import io.qml4j.render.items.shape.ImageFill;

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
}
