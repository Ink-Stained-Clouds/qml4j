package io.qml4j.render.items.core;

import io.qml4j.engine.binding.Property;
import io.qml4j.render.Painter;

public class Image extends Item {
    public final Property<String> source = new Property<>(null);
    // String ("Stretch"/"PreserveAspectCrop"/...) or the Image.* fillMode enum (a Long);
    // Object so the enum value doesn't fail the typed read/listener cast.
    public final Property<Object> fillMode = new Property<>("Stretch");
    public final Property<Boolean> asynchronous = new Property<>(Boolean.FALSE);
    public final Property<Boolean> cache = new Property<>(Boolean.TRUE);
    public final Size sourceSize = new Size();
    public final Property<Number> horizontalAlignment = new Property<>(4); // AlignHCenter
    public final Property<Number> verticalAlignment = new Property<>(128); // AlignVCenter
    public final Property<Number> paintedWidth = new Property<>(0);
    public final Property<Number> paintedHeight = new Property<>(0);
    // Image.Null=0 / Ready=1 / Loading=2 / Error=3 (Qt). MD3 spinners bind to it.
    public final Property<Number> status = new Property<>(0);

    public io.github.humbleui.skija.Image skiaImage;
    public String loadedSource;
    public int intrinsicWidth;
    public int intrinsicHeight;
    // Async fetch of a remote (http) source: a background thread fills fetchedBytes and
    // sets fetchDone; the render thread decodes + sets status (Skija stays single-thread).
    public volatile byte[] fetchedBytes;
    public volatile boolean fetchDone;
    public boolean fetchStarted;

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        p.drawImage(this, w, h);
    }
}
