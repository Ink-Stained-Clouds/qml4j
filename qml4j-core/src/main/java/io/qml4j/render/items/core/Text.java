package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;

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
    public float effectiveFontSize() {
        float fp = font.pixelSize.peek().floatValue();
        return fp > 0 ? fp : fontSize.peek().floatValue();
    }

    public String lastMeasuredText;
    public float lastMeasuredSize = -1f;
    public double lastSetWidth = Double.NaN;
    public double lastSetHeight = Double.NaN;
}
