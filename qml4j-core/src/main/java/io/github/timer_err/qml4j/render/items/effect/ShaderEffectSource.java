package io.github.timer_err.qml4j.render.items.effect;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;

// QtQuick ShaderEffectSource. v0 stub: holds sourceItem and the common knobs so a
// document that captures a subtree (e.g. a blur card) loads. It does not yet
// re-render the captured texture, so no blur/effect is produced.
public class ShaderEffectSource extends Item {
    @SuppressWarnings("unused")
    public final Property<Object> sourceItem = new Property<>(null);
    @SuppressWarnings("unused")
    public final Property<Boolean> live = new Property<>(Boolean.TRUE);
    @SuppressWarnings("unused")
    public final Property<Boolean> recursive = new Property<>(Boolean.FALSE);
    @SuppressWarnings("unused")
    public final Property<Boolean> hideSource = new Property<>(Boolean.FALSE);
    @SuppressWarnings("unused")
    public final Property<Boolean> mipmap = new Property<>(Boolean.FALSE);
    @SuppressWarnings("unused")
    public final Property<Object> textureSize = new Property<>(null);
    @SuppressWarnings("unused")
    public final Property<Object> sourceRect = new Property<>(null);
    @SuppressWarnings("unused")
    public final Property<String> wrapMode = new Property<>("ClampToEdge");
    @SuppressWarnings("unused")
    public final Property<String> format = new Property<>("RGBA8");
    @SuppressWarnings("unused")
    public final Property<Number> samples = new Property<>(0);
}
