package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;

// QML font value-type group (font.pixelSize / font.family / font.weight / ...).
// pixelSize defaults to 0 = "unset": the renderer then falls back to the
// flat fontSize property. family/weight are accepted but not yet applied
// (single built-in font face).
public final class Font {
    public final Property<String> family = new Property<>("");
    public final Property<Number> pixelSize = new Property<>(0);
    public final Property<Number> pointSize = new Property<>(0);
    public final Property<Number> weight = new Property<>(50);
    public final Property<Boolean> bold = new Property<>(Boolean.FALSE);
    public final Property<Boolean> italic = new Property<>(Boolean.FALSE);
    // Font.MixedCase(0)/AllUppercase/AllLowercase/... — accepted; not yet applied.
    public final Property<Number> capitalization = new Property<>(0);
}
