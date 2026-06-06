package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;
import io.qml4j.render.Painter;

public class Rectangle extends Item {
    public final Property<String> color = new Property<>("#ffffff");
    public final Property<Number> radius = new Property<>(0);
    // Per-corner radius; -1 = unset (falls back to `radius`).
    public final Property<Number> topLeftRadius = new Property<>(-1);
    public final Property<Number> topRightRadius = new Property<>(-1);
    public final Property<Number> bottomLeftRadius = new Property<>(-1);
    public final Property<Number> bottomRightRadius = new Property<>(-1);
    public final Border border = new Border();

    public float cornerRadius(float corner) {
        return corner >= 0 ? corner : radius.peekFloat();
    }
    public final Property<Gradient> gradient = new Property<>(null);

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        float radius = Math.max(0f, this.radius.peekFloat());
        float borderWidth = Math.max(0f, border.width.peekFloat());
        Gradient g = gradient.peek();
        if (g != null) {
            p.fillGradientRoundRect(0, 0, w, h, radius, g, alpha);
        } else {
            p.fillRoundRect(0, 0, w, h, radius, p.alphaColor(color.peek(), alpha));
        }
        if (borderWidth > 0f) {
            float inset = borderWidth / 2f;
            float bw = Math.max(0f, w - borderWidth);
            float bh = Math.max(0f, h - borderWidth);
            float br = radius > 0f ? Math.max(0f, radius - inset) : 0f;
            p.strokeRoundRect(inset, inset, bw, bh, br,
                p.alphaColor(border.color.peek(), alpha), borderWidth);
        }
    }
}
