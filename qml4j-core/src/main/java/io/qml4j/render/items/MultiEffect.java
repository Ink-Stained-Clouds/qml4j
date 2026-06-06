package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

// QtQuick.Effects MultiEffect. v0 supports source + mask (maskEnabled/maskSource):
// the source item is painted at the effect's geometry, clipped to the mask's
// shape. Colour/blur/shadow knobs are accepted but not yet applied.
public class MultiEffect extends Item {
    public final Property<Object> source = new Property<>(null);
    public final Property<Boolean> maskEnabled = new Property<>(Boolean.FALSE);
    public final Property<Object> maskSource = new Property<>(null);
    public final Property<Boolean> maskInverted = new Property<>(Boolean.FALSE);

    public final Property<Boolean> blurEnabled = new Property<>(Boolean.FALSE);
    public final Property<Number> blur = new Property<>(0);
    public final Property<Number> blurMax = new Property<>(32);
    public final Property<Boolean> shadowEnabled = new Property<>(Boolean.FALSE);
    public final Property<String> shadowColor = new Property<>("#000000");
    public final Property<Number> shadowBlur = new Property<>(0);
    public final Property<Number> shadowVerticalOffset = new Property<>(0);
    public final Property<Number> shadowHorizontalOffset = new Property<>(0);
    public final Property<Number> shadowOpacity = new Property<>(1.0);
    public final Property<Number> shadowScale = new Property<>(1.0);
    public final Property<Boolean> colorizationEnabled = new Property<>(Boolean.FALSE);
    public final Property<Number> brightness = new Property<>(0);
    public final Property<Number> contrast = new Property<>(0);
    public final Property<Number> saturation = new Property<>(0);
}
