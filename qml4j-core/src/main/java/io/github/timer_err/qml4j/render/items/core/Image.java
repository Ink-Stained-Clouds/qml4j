package io.github.timer_err.qml4j.render.items.core;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.Painter;

public class Image extends Item {
    public final Property<String> source = new Property<>(null);
    // String ("Stretch"/"PreserveAspectCrop"/...) or the Image.* fillMode enum (a Long);
    // Object so the enum value doesn't fail the typed read/listener cast.
    public final Property<Object> fillMode = new Property<>("Stretch");
    @SuppressWarnings("unused")
    public final Property<Boolean> asynchronous = new Property<>(Boolean.FALSE);
    @SuppressWarnings("unused")
    public final Property<Boolean> cache = new Property<>(Boolean.TRUE);
    // Rounded corners clipped directly at draw time (clipRRect). Lets a list/grid
    // delegate get a rounded cover without a layer.effect mask -- the mask path
    // allocates an offscreen surface (saveLayer) per delegate every frame, which
    // is the dominant cost when scrolling a grid of cover images.
    public final Property<Number> radius = new Property<>(0);
    public final Size sourceSize = new Size();
    @SuppressWarnings("unused")
    public final Property<Number> horizontalAlignment = new Property<>(4); // AlignHCenter
    @SuppressWarnings("unused")
    public final Property<Number> verticalAlignment = new Property<>(128); // AlignVCenter
    public final Property<Number> paintedWidth = new Property<>(0);
    public final Property<Number> paintedHeight = new Property<>(0);
    // Image.Null=0 / Ready=1 / Loading=2 / Error=3 (Qt). MD3 spinners bind to it.
    public final Property<Number> status = new Property<>(0);

    public io.github.humbleui.skija.Image skiaImage;   // render-thread only
    public String loadedSource;
    public int intrinsicWidth;
    public int intrinsicHeight;
    // Async decode: a background thread loads + decodes the source (local or remote) into
    // a raster image so the render thread never blocks on makeFromEncoded/downscale (a
    // song-switch cover or a scrolling list of thumbnails would otherwise stall a frame).
    // The render thread bumps decodeGen on a source change; the worker publishes the
    // decoded image + decodeReadyGen for that gen; the render thread adopts it (and is the
    // only one to set the status Property / touch skiaImage).
    public volatile long decodeGen;
    public volatile long decodeReadyGen = -1;
    public volatile io.github.humbleui.skija.Image pendingImage;
    public volatile int pendW, pendH;
    public long adoptedGen = -1;   // render-thread only

    public Image() {
        wireContentInvalidation(source, fillMode, radius, horizontalAlignment, verticalAlignment,
            status, sourceSize.width, sourceSize.height);
    }

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        p.drawImage(this, w, h, alpha);
    }

    // Close the decoded native image when this item is discarded (e.g. a list
    // delegate scrolled out of a model swap) — it isn't GC-managed, so a row's
    // cover would otherwise leak its native memory.
    @Override
    protected void releaseResources() {
        if (skiaImage != null) {
            skiaImage.close();
            skiaImage = null;
        }
        io.github.humbleui.skija.Image pend = pendingImage;
        if (pend != null) {
            pend.close();
            pendingImage = null;
        }
        decodeGen++;   // invalidate any in-flight decode so it discards its result
        loadedSource = null;
    }
}
