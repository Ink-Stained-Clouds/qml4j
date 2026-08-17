package io.github.timer_err.qml4j.render;

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
import io.github.humbleui.types.RRect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

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
    // The current path as a replayable command buffer, NOT a live PathBuilder: in
    // Skija 0.143 a PathBuilder is unusable after build() (the next reset()/op
    // segfaults), so each fill/stroke/clip builds a fresh single-use PathBuilder
    // from these commands. Encoded as primitive floats ([opcode, args...]) in a
    // reused, grown buffer rather than one captured lambda per op -- a Canvas that
    // strokes a per-frame waveform issues hundreds of moveTo/lineTo a frame, and a
    // lambda each was the lyric page's dominant per-frame allocation.
    private static final int OP_MOVE = 0, OP_LINE = 1, OP_CLOSE = 2, OP_RECT = 3,
        OP_RRECT = 4, OP_OVAL = 5, OP_ARC = 6, OP_TANGENT_ARC = 7;
    private float[] cmd = new float[64];
    private int cmdLen = 0;

    private void push(float v) {
        if (cmdLen >= cmd.length) cmd = Arrays.copyOf(cmd, cmd.length * 2);
        cmd[cmdLen++] = v;
    }

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

    @SuppressWarnings("unused")
    private static final class State {
        Object fill, stroke; double lw, ga; String cap, join, fnt, tAlign, tBase; float[] dash;
    }
    private final Deque<State> stack = new ArrayDeque<>();

    Context2D(Canvas canvas, Renderer renderer) {
        this.canvas = canvas;
        this.renderer = renderer;
    }

    // ---- path ----
    @SuppressWarnings("unused")
    public void beginPath() { cmdLen = 0; }
    @SuppressWarnings("unused")
    public void closePath() { push(OP_CLOSE); }
    @SuppressWarnings("unused")
    public void moveTo(double x, double y) {
        push(OP_MOVE); push((float) x); push((float) y);
    }
    @SuppressWarnings("unused")
    public void lineTo(double x, double y) {
        push(OP_LINE); push((float) x); push((float) y);
    }

    // qml4j extension for animated waveforms: append the same sampled polyline a
    // JavaScript moveTo/lineTo loop would produce, but cross the JS/Java bridge only
    // once. The equation deliberately uses doubles and casts only while recording
    // each command, matching calls from QML bit-for-bit.
    //
    // exactEnd=false: sample startX, startX + step, ... while x <= endX.
    // exactEnd=true:  sample while x < endX, then append a point exactly at endX.
    // The latter keeps a determinate progress tip moving continuously rather than
    // snapping it to the sampling grid. An empty/reversed interval adds no command.
    @SuppressWarnings("unused")
    public void appendSineWave(double startX, double endX, double step,
                               double centerY, double amplitude, double frequency,
                               double phase, boolean exactEnd) {
        if (!Double.isFinite(startX) || !Double.isFinite(endX)
                || !Double.isFinite(step) || step <= 0
                || !Double.isFinite(centerY) || !Double.isFinite(amplitude)
                || !Double.isFinite(frequency) || !Double.isFinite(phase)
                || startX > endX) {
            return;
        }

        boolean begun = false;
        if (exactEnd) {
            for (double x = startX; x < endX; x += step) {
                appendWavePoint(begun ? OP_LINE : OP_MOVE, x, centerY,
                    amplitude, frequency, phase);
                begun = true;
            }
            if (begun) {
                appendWavePoint(OP_LINE, endX, centerY, amplitude, frequency, phase);
            }
        } else {
            for (double x = startX; x <= endX; x += step) {
                appendWavePoint(begun ? OP_LINE : OP_MOVE, x, centerY,
                    amplitude, frequency, phase);
                begun = true;
            }
        }
    }

    private void appendWavePoint(int op, double x, double centerY,
                                 double amplitude, double frequency, double phase) {
        double y = centerY + amplitude * Math.sin((x * frequency) + phase);
        push(op); push((float) x); push((float) y);
    }
    @SuppressWarnings("unused")
    public void rect(double x, double y, double w, double h) {
        push(OP_RECT); push((float) x); push((float) y); push((float) w); push((float) h);
    }

    // Qt Context2D.roundedRect(x, y, w, h, xRadius, yRadius) -- rounded-rect subpath.
    @SuppressWarnings("unused")
    public void roundedRect(double x, double y, double w, double h, double xr, double yr) {
        push(OP_RRECT);
        push((float) x); push((float) y); push((float) w); push((float) h);
        push((float) xr); push((float) yr);
    }

    // HTML arc(cx, cy, r, startRad, endRad, counterclockwise=false).
    public void arc(double cx, double cy, double r, double start, double end, boolean ccw) {
        float a0 = (float) Math.toDegrees(start);
        float sweep = (float) Math.toDegrees(end - start);
        if (ccw && sweep > 0) sweep -= 360;
        if (!ccw && sweep < 0) sweep += 360;
        float l = (float) (cx - r), t = (float) (cy - r), ri = (float) (cx + r), bo = (float) (cy + r);
        if (Math.abs(sweep) >= 359.999f) {
            // A full circle: Skija's arcTo treats a 360 sweep as empty, so add the oval.
            push(OP_OVAL); push(l); push(t); push(ri); push(bo);
            return;
        }
        // The first path op starts a fresh subpath -> move to the arc start; a later arc
        // connects from the current point (HTML arc semantics).
        float moveToStart = cmdLen == 0 ? 1f : 0f;
        push(OP_ARC); push(l); push(t); push(ri); push(bo); push(a0); push(sweep); push(moveToStart);
    }

    @SuppressWarnings("unused")
    public void arc(double cx, double cy, double r, double start, double end) {
        arc(cx, cy, r, start, end, false);
    }

    @SuppressWarnings("unused")
    public void arcTo(double x1, double y1, double x2, double y2, double r) {
        push(OP_TANGENT_ARC);
        push((float) x1); push((float) y1); push((float) x2); push((float) y2); push((float) r);
    }

    // ---- fills / strokes ----
    @SuppressWarnings("unused")
    public void fill() {
        withPath(p -> fillWith(fillStyle, paint -> canvas.drawPath(p, paint)));
    }

    @SuppressWarnings("unused")
    public void stroke() {
        withPath(p -> strokeWith(paint -> canvas.drawPath(p, paint)));
    }

    @SuppressWarnings("unused")
    public void clip() {
        withPath(p -> canvas.clipPath(p, true));
    }

    // Build a fresh single-use PathBuilder by replaying the command buffer, hand the
    // built Path to `body`, and close both natives. (A PathBuilder cannot be reused
    // after build() in this Skija version.) Only the rare object-shaped ops (rect/
    // rrect/oval/arc) allocate a Rect at replay; the common move/line/close don't.
    private void withPath(Consumer<Path> body) {
        try (PathBuilder b = new PathBuilder()) {
            b.setFillMode(PathFillMode.WINDING);
            int i = 0;
            while (i < cmdLen) {
                int op = (int) cmd[i++];
                switch (op) {
                    case OP_MOVE: b.moveTo(cmd[i], cmd[i + 1]); i += 2; break;
                    case OP_LINE: b.lineTo(cmd[i], cmd[i + 1]); i += 2; break;
                    case OP_CLOSE: b.closePath(); break;
                    case OP_RECT:
                        b.addRect(Rect.makeXYWH(cmd[i], cmd[i + 1], cmd[i + 2], cmd[i + 3])); i += 4; break;
                    case OP_RRECT:
                        b.addRRect(RRect.makeXYWH(cmd[i], cmd[i + 1], cmd[i + 2], cmd[i + 3],
                            cmd[i + 4], cmd[i + 5])); i += 6; break;
                    case OP_OVAL:
                        b.addOval(Rect.makeLTRB(cmd[i], cmd[i + 1], cmd[i + 2], cmd[i + 3])); i += 4; break;
                    case OP_ARC:
                        b.arcTo(Rect.makeLTRB(cmd[i], cmd[i + 1], cmd[i + 2], cmd[i + 3]),
                            cmd[i + 4], cmd[i + 5], cmd[i + 6] != 0f); i += 7; break;
                    case OP_TANGENT_ARC:
                        b.tangentArcTo(cmd[i], cmd[i + 1], cmd[i + 2], cmd[i + 3], cmd[i + 4]); i += 5; break;
                    default: break;
                }
            }
            try (Path p = b.build()) { body.accept(p); }
        }
    }

    @SuppressWarnings("unused")
    public void fillRect(double x, double y, double w, double h) {
        Rect r = Rect.makeXYWH((float) x, (float) y, (float) w, (float) h);
        fillWith(fillStyle, p -> canvas.drawRect(r, p));
    }

    @SuppressWarnings("unused")
    public void strokeRect(double x, double y, double w, double h) {
        Rect r = Rect.makeXYWH((float) x, (float) y, (float) w, (float) h);
        strokeWith(p -> canvas.drawRect(r, p));
    }

    @SuppressWarnings("unused")
    public void clearRect(double x, double y, double w, double h) {
        try (Paint p = new Paint()) {
            p.setColor(0);
            p.setBlendMode(BlendMode.CLEAR);
            canvas.drawRect(Rect.makeXYWH((float) x, (float) y, (float) w, (float) h), p);
        }
    }

    @SuppressWarnings("unused")
    public void fillText(String text, double x, double y) {
        if (text == null) return;
        // fontFor returns a cached Font shared across frames -- must NOT be closed.
        Font f = renderer.fonts().fontFor(fontSize(), text);
        float ax = (float) x;
        if ("center".equals(textAlign)) ax -= f.measureTextWidth(text) / 2f;
        else if ("right".equals(textAlign) || "end".equals(textAlign)) ax -= f.measureTextWidth(text);
        final float bx = ax;
        final float by = (float) y + baselineOffset(f);
        fillWith(fillStyle, p -> canvas.drawString(text, bx, by, f, p));
    }

    // drawString places the baseline at `y`; shift so `y` means the edge the HTML
    // textBaseline names (default "alphabetic" already is the baseline).
    private float baselineOffset(Font f) {
        switch (textBaseline) {
            case "top":
            case "hanging":
                return -f.getMetrics().getAscent();
            case "middle":
                return -(f.getMetrics().getAscent() + f.getMetrics().getDescent()) / 2f;
            case "bottom":
            case "ideographic":
                return -f.getMetrics().getDescent();
            default:
                return 0f;
        }
    }

    // ---- transform / state ----
    @SuppressWarnings("unused")
    public void translate(double x, double y) { canvas.translate((float) x, (float) y); }
    @SuppressWarnings("unused")
    public void rotate(double rad) { canvas.rotate((float) Math.toDegrees(rad)); }
    @SuppressWarnings("unused")
    public void scale(double x, double y) { canvas.scale((float) x, (float) y); }

    @SuppressWarnings("unused")
    public void save() {
        canvas.save();
        State s = new State();
        s.fill = fillStyle; s.stroke = strokeStyle; s.lw = lineWidth; s.ga = globalAlpha;
        s.cap = lineCap; s.join = lineJoin; s.fnt = font; s.tAlign = textAlign; s.tBase = textBaseline;
        s.dash = lineDash;
        stack.push(s);
    }

    @SuppressWarnings("unused")
    public void restore() {
        canvas.restore();
        State s = stack.poll();
        if (s == null) return;
        fillStyle = s.fill; strokeStyle = s.stroke; lineWidth = s.lw; globalAlpha = s.ga;
        lineCap = s.cap; lineJoin = s.join; font = s.fnt; textAlign = s.tAlign; textBaseline = s.tBase;
        lineDash = s.dash;
    }

    // Canvas/Context2D reset: clear the path + drawing state.
    @SuppressWarnings("unused")
    public void reset() {
        cmdLen = 0;
        stack.clear();
        fillStyle = "#000000"; strokeStyle = "#000000"; lineWidth = 1; globalAlpha = 1;
        lineCap = "butt"; lineJoin = "miter"; lineDash = null;
    }

    @SuppressWarnings("unused")
    public void setLineDash(Object dashes) {
        if (!(dashes instanceof List)) { lineDash = null; return; }
        List<?> l = (List<?>) dashes;
        if (l.isEmpty()) { lineDash = null; return; }
        float[] d = new float[l.size()];
        for (int i = 0; i < d.length; i++) d[i] = ((Number) l.get(i)).floatValue();
        lineDash = d;
    }

    @SuppressWarnings("unused")
    public RadialGradient createRadialGradient(double x0, double y0, double r0,
                                               double x1, double y1, double r1) {
        return new RadialGradient((float) x1, (float) y1, (float) r1);
    }

    // ---- paint construction (every native handle closed) ----
    private interface DrawOp { void draw(Paint p); }

    private void fillWith(Object style, DrawOp op) {
        try (Paint p = new Paint()) {
            p.setAntiAlias(true);
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
            p.setAntiAlias(true);
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
        @SuppressWarnings("unused")
        RadialGradient(float cx, float cy, float radius) { this.cx = cx; this.cy = cy; this.radius = radius; }
        @SuppressWarnings("unused")
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
