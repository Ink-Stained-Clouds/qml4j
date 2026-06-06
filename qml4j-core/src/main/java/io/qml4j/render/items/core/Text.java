package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;
import io.qml4j.render.Painter;
import io.qml4j.render.TextLayout;

public class Text extends Item {
    public final Property<String> text = new Property<>("");
    public final Property<String> color = new Property<>("#000000");
    public final Property<Number> fontSize = new Property<>(14);
    public final Font font = new Font();
    public final Property<Number> wrapMode = new Property<>(0);            // Text.NoWrap
    public final Property<Number> horizontalAlignment = new Property<>(1); // Text.AlignLeft
    public final Property<Number> verticalAlignment = new Property<>(32);  // Text.AlignTop
    public final Property<Number> elide = new Property<>(0);                // Text.ElideNone

    // Effective pixel size: Qt's font.pixelSize wins when set, else flat fontSize.
    // Either may be null (a binding that evaluated to undefined -- QML-tolerant).
    public float effectiveFontSize() {
        Number fp = font.pixelSize.peek();
        if (fp != null && fp.floatValue() > 0) return fp.floatValue();
        Number fs = fontSize.peek();
        return fs != null ? fs.floatValue() : 14f;
    }

    public String lastMeasuredText;
    public float lastMeasuredSize = -1f;
    public double lastSetWidth = Double.NaN;
    public double lastSetHeight = Double.NaN;

    @Override
    public void measure(TextLayout t) {
        t.measureText(this);
    }

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        int argb = p.alphaColor(color.peek(), alpha);
        float size = effectiveFontSize();
        String ig = p.iconGlyphFor(this);
        if (ig != null) {
            if (!ig.isEmpty()) p.drawIconGlyph(ig, h, argb, size);
            return;
        }
        String s = p.displayTextFor(this);
        if (s.isEmpty()) return;
        boolean elideRight = elide.peek().intValue() == 3; // Text.ElideRight
        p.drawWrappedText(s, w, argb, size, wrapMode.peek().intValue(), elideRight);
    }
}
