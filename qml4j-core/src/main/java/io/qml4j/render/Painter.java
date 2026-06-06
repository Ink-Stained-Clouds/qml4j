package io.qml4j.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import io.qml4j.render.items.core.Gradient;
import io.qml4j.render.items.core.GradientStop;

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
}
