package io.qml4j.render;

import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PathEffect;
import io.github.humbleui.skija.PathFillMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// The HTML5 2D drawing context handed to a Canvas item's onPaint handler. JS calls
// its public methods/fields (bridged by JsWrap), which issue Skija draw commands to
// the canvas the Renderer already translated to the Canvas item's origin -- so all
// coordinates are local to the canvas, matching the web API. Lives in the render
// package (items never touch skija); a Canvas item holds one only as an opaque handle.
//
// Skija natives (Paint/Path/Shader/PathEffect/Font) are NOT GC-managed -- every one
// is closed explicitly: per-op paints/shaders/effects via try-with-resources, the
// accumulating Path on beginPath/reset and on dispose() at end of frame.
public final class Context2D {

    private final Canvas canvas;
    private final Renderer renderer;
    private final PathBuilder pb = new PathBuilder();

    // Drawing state (read/written from JS as `ctx.fillStyle = ...`, etc.).
    public Object fillStyle = "#000000";
    public Object strokeStyle = "#000000";
    public double lineWidth = 1;
    public double globalAlpha = 1;
    public String lineCap = "butt";
    public String lineJoin = "miter";
    public String font = "10px sans-serif";
    public String textAlign = "start";
    public String textBaseline = "alphabetic";

    private float[] lineDash = null;

    private static final class State {
        Object fill, stroke; double lw, ga; String cap, join, fnt, tAlign, tBase; float[] dash;
    }
    private final Deque<State> stack = new ArrayDeque<>();

    Context2D(Canvas canvas, Renderer renderer) {
        this.canvas = canvas;
        this.renderer = renderer;
    }

    // ---- path ----
    public void beginPath() { pb.reset(); }
    public void closePath() { pb.closePath(); }
    public void moveTo(double x, double y) { pb.moveTo((float) x, (float) y); }
    public void lineTo(double x, double y) { pb.lineTo((float) x, (float) y); }
    public void rect(double x, double y, double w, double h) {
        pb.addRect(Rect.makeXYWH((float) x, (float) y, (float) w, (float) h));
    }

    // HTML arc(cx, cy, r, startRad, endRad, counterclockwise=false).
    public void arc(double cx, double cy, double r, double start, double end, boolean ccw) {
        float a0 = (float) Math.toDegrees(start);
        float sweep = (float) Math.toDegrees(end - start);
        if (ccw && sweep > 0) sweep -= 360;
        if (!ccw && sweep < 0) sweep += 360;
        Rect oval = Rect.makeLTRB((float) (cx - r), (float) (cy - r), (float) (cx + r), (float) (cy + r));
        pb.arcTo(oval, a0, sweep, false);
    }

    public void arc(double cx, double cy, double r, double start, double end) {
        arc(cx, cy, r, start, end, false);
    }

    public void arcTo(double x1, double y1, double x2, double y2, double r) {
        pb.tangentArcTo((float) r, (float) x1, (float) y1, (float) x2, (float) y2);
    }

    // ---- fills / strokes ----
    public void fill() {
        pb.setFillMode(PathFillMode.WINDING);
        try (Path pth = pb.build()) { fillWith(fillStyle, p -> canvas.drawPath(pth, p)); }
    }

    public void stroke() {
        try (Path pth = pb.build()) { strokeWith(p -> canvas.drawPath(pth, p)); }
    }

    public void clip() {
        try (Path pth = pb.build()) { canvas.clipPath(pth, true); }
    }

    public void fillRect(double x, double y, double w, double h) {
        Rect r = Rect.makeXYWH((float) x, (float) y, (float) w, (float) h);
        fillWith(fillStyle, p -> canvas.drawRect(r, p));
    }

    public void strokeRect(double x, double y, double w, double h) {
        Rect r = Rect.makeXYWH((float) x, (float) y, (float) w, (float) h);
        strokeWith(p -> canvas.drawRect(r, p));
    }

    public void clearRect(double x, double y, double w, double h) {
        try (Paint p = new Paint()) {
            p.setColor(0);
            p.setBlendMode(BlendMode.CLEAR);
            canvas.drawRect(Rect.makeXYWH((float) x, (float) y, (float) w, (float) h), p);
        }
    }

    public void fillText(String text, double x, double y) {
        if (text == null) return;
        try (Font f = renderer.fonts().fontFor(fontSize(), text)) {
            float tx = (float) x;
            if ("center".equals(textAlign)) tx -= f.measureTextWidth(text) / 2f;
            else if ("right".equals(textAlign) || "end".equals(textAlign)) tx -= f.measureTextWidth(text);
            float bx = tx;
            float by = (float) y;
            fillWith(fillStyle, p -> canvas.drawString(text, bx, by, f, p));
        }
    }

    // ---- transform / state ----
    public void translate(double x, double y) { canvas.translate((float) x, (float) y); }
    public void rotate(double rad) { canvas.rotate((float) Math.toDegrees(rad)); }
    public void scale(double x, double y) { canvas.scale((float) x, (float) y); }

    public void save() {
        canvas.save();
        State s = new State();
        s.fill = fillStyle; s.stroke = strokeStyle; s.lw = lineWidth; s.ga = globalAlpha;
        s.cap = lineCap; s.join = lineJoin; s.fnt = font; s.tAlign = textAlign; s.tBase = textBaseline;
        s.dash = lineDash;
        stack.push(s);
    }

    public void restore() {
        canvas.restore();
        State s = stack.poll();
        if (s == null) return;
        fillStyle = s.fill; strokeStyle = s.stroke; lineWidth = s.lw; globalAlpha = s.ga;
        lineCap = s.cap; lineJoin = s.join; font = s.fnt; textAlign = s.tAlign; textBaseline = s.tBase;
        lineDash = s.dash;
    }

    // Canvas/Context2D reset: clear the path + drawing state.
    public void reset() {
        pb.reset();
        stack.clear();
        fillStyle = "#000000"; strokeStyle = "#000000"; lineWidth = 1; globalAlpha = 1;
        lineCap = "butt"; lineJoin = "miter"; lineDash = null;
    }

    public void setLineDash(Object dashes) {
        if (!(dashes instanceof List)) { lineDash = null; return; }
        List<?> l = (List<?>) dashes;
        if (l.isEmpty()) { lineDash = null; return; }
        float[] d = new float[l.size()];
        for (int i = 0; i < d.length; i++) d[i] = ((Number) l.get(i)).floatValue();
        lineDash = d;
    }

    public RadialGradient createRadialGradient(double x0, double y0, double r0,
                                               double x1, double y1, double r1) {
        return new RadialGradient((float) x1, (float) y1, (float) r1);
    }

    // Release the accumulating PathBuilder's native memory; called once per frame
    // after onPaint.
    public void dispose() {
        pb.close();
    }

    // ---- paint construction (every native handle closed) ----
    private interface DrawOp { void draw(Paint p); }

    private void fillWith(Object style, DrawOp op) {
        try (Paint p = new Paint()) {
            p.setMode(PaintMode.FILL);
            Shader sh = style instanceof RadialGradient ? ((RadialGradient) style).toShader() : null;
            try {
                if (sh != null) { p.setShader(sh); if (globalAlpha < 1) p.setAlphaf((float) globalAlpha); }
                else p.setColor(colorOf(style));
                op.draw(p);
            } finally {
                if (sh != null) sh.close();
            }
        }
    }

    private void strokeWith(DrawOp op) {
        try (Paint p = new Paint()) {
            p.setMode(PaintMode.STROKE);
            p.setStrokeWidth((float) lineWidth);
            p.setStrokeCap("round".equals(lineCap) ? PaintStrokeCap.ROUND
                : "square".equals(lineCap) ? PaintStrokeCap.SQUARE : PaintStrokeCap.BUTT);
            p.setStrokeJoin("round".equals(lineJoin) ? PaintStrokeJoin.ROUND
                : "bevel".equals(lineJoin) ? PaintStrokeJoin.BEVEL : PaintStrokeJoin.MITER);
            PathEffect dash = lineDash != null ? PathEffect.makeDash(lineDash, 0) : null;
            Shader sh = strokeStyle instanceof RadialGradient ? ((RadialGradient) strokeStyle).toShader() : null;
            try {
                if (dash != null) p.setPathEffect(dash);
                if (sh != null) { p.setShader(sh); if (globalAlpha < 1) p.setAlphaf((float) globalAlpha); }
                else p.setColor(colorOf(strokeStyle));
                op.draw(p);
            } finally {
                if (dash != null) dash.close();
                if (sh != null) sh.close();
            }
        }
    }

    private int colorOf(Object style) {
        int argb = Renderer.parseColor(String.valueOf(style));
        if (globalAlpha < 1) {
            int a = (int) Math.round(((argb >>> 24) & 0xFF) * globalAlpha);
            argb = (a << 24) | (argb & 0xFFFFFF);
        }
        return argb;
    }

    private float fontSize() {
        int px = font == null ? -1 : font.indexOf("px");
        if (px > 0) {
            int start = px;
            while (start > 0 && (Character.isDigit(font.charAt(start - 1)) || font.charAt(start - 1) == '.')) start--;
            try { return Float.parseFloat(font.substring(start, px).trim()); } catch (NumberFormatException ignore) { }
        }
        return 10f;
    }

    // A radial gradient (the only gradient MD3 canvases use); addColorStop collects
    // stops, toShader builds the Skija shader at draw time (closed by the caller).
    public static final class RadialGradient {
        private final float cx, cy, radius;
        private final List<Float> stops = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        RadialGradient(float cx, float cy, float radius) { this.cx = cx; this.cy = cy; this.radius = radius; }
        public void addColorStop(double offset, String color) {
            stops.add((float) offset);
            colors.add(Renderer.parseColor(color));
        }
        Shader toShader() {
            int[] c = new int[colors.size()];
            float[] s = new float[stops.size()];
            for (int i = 0; i < c.length; i++) { c[i] = colors.get(i); s[i] = stops.get(i); }
            return Shader.makeRadialGradient(cx, cy, Math.max(radius, 0.01f), c, s);
        }
    }
}
