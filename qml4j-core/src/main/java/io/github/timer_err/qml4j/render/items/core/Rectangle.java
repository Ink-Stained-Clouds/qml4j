package io.github.timer_err.qml4j.render.items.core;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.Painter;

public class Rectangle extends Item {
    public final Property<String> color = new Property<>("#ffffff");
    public final Property<Number> radius = new Property<>(0);
    // Per-corner radius; -1 = unset (falls back to `radius`).
    public final Property<Number> topLeftRadius = new Property<>(-1);
    public final Property<Number> topRightRadius = new Property<>(-1);
    public final Property<Number> bottomLeftRadius = new Property<>(-1);
    public final Property<Number> bottomRightRadius = new Property<>(-1);
    public final Border border = new Border();

    // Negative is clamped, not passed through: `radius` accepts any number and this is the
    // fallback for an unset corner, so a negative radius would otherwise travel into the
    // per-corner path while the uniform path clamps it. Skia reads a negative radius as zero, so
    // the two agree today by coincidence rather than by contract.
    public float cornerRadius(float corner) {
        return Math.max(0f, corner >= 0f ? corner : radius.peekFloat());
    }
    public final Property<Gradient> gradient = new Property<>(null);

    public Rectangle() {
        wireContentInvalidation(color, radius, topLeftRadius, topRightRadius,
            bottomLeftRadius, bottomRightRadius, gradient, border.width, border.color);
    }

    @Override
    protected void wireDeferredContentInvalidation() {
        // A gradient's stop colours/positions live on GradientStop (a QObject); wire them so an
        // animated stop re-records. The `gradient` reference change is already wired above.
        wireHolderContent(gradient.peek());
    }

    // True when any corner overrides `radius`, so the uniform path stays the common case.
    private boolean hasPerCornerRadius() {
        return topLeftRadius.peekFloat() >= 0f || topRightRadius.peekFloat() >= 0f
            || bottomRightRadius.peekFloat() >= 0f || bottomLeftRadius.peekFloat() >= 0f;
    }

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        fill(p, w, h, alpha);
        strokeBorder(p, w, h, alpha);
    }

    private void fill(Painter p, float w, float h, float alpha) {
        Gradient g = gradient.peek();
        if (g != null) {
            // A gradient fill has no per-corner primitive, so the uniform radius is its shape.
            p.fillGradientRoundRect(0, 0, w, h, uniformRadius(), g, alpha);
            return;
        }
        int argb = p.alphaColor(color.peek(), alpha);
        if (hasPerCornerRadius()) {
            p.fillRoundRect(0, 0, w, h,
                cornerRadius(topLeftRadius.peekFloat()), cornerRadius(topRightRadius.peekFloat()),
                cornerRadius(bottomRightRadius.peekFloat()), cornerRadius(bottomLeftRadius.peekFloat()),
                argb);
        } else {
            p.fillRoundRect(0, 0, w, h, uniformRadius(), argb);
        }
    }

    private void strokeBorder(Painter p, float w, float h, float alpha) {
        float borderWidth = Math.max(0f, border.width.peekFloat());
        if (borderWidth <= 0f) {
            return;
        }
        // The stroke runs along the box's midline, so the box shrinks by the full width and every
        // corner tightens by half of it.
        float inset = borderWidth / 2f;
        float bw = Math.max(0f, w - borderWidth);
        float bh = Math.max(0f, h - borderWidth);
        int argb = p.alphaColor(border.color.peek(), alpha);
        if (hasPerCornerRadius()) {
            p.strokeRoundRect(inset, inset, bw, bh,
                tightened(cornerRadius(topLeftRadius.peekFloat()), inset),
                tightened(cornerRadius(topRightRadius.peekFloat()), inset),
                tightened(cornerRadius(bottomRightRadius.peekFloat()), inset),
                tightened(cornerRadius(bottomLeftRadius.peekFloat()), inset),
                argb, borderWidth);
        } else {
            p.strokeRoundRect(inset, inset, bw, bh, tightened(uniformRadius(), inset),
                argb, borderWidth);
        }
    }

    private float uniformRadius() {
        return Math.max(0f, radius.peekFloat());
    }

    private static float tightened(float corner, float inset) {
        return corner > 0f ? Math.max(0f, corner - inset) : 0f;
    }
}
