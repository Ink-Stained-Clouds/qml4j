package io.github.timer_err.qml4j.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.DirectContext;

public interface SurfaceBackend {
    void init(int width, int height);
    Canvas acquireCanvas();
    void present();
    void resize(int width, int height);
    void dispose();
    int width();
    int height();

    // The GPU context backing this surface, or null for a raster (CPU) backend. A Canvas
    // item's offscreen cache must be made on the same context to blit correctly -- a raster
    // offscreen drawn onto a GPU canvas only composites once.
    default DirectContext recordingContext() {
        return null;
    }
}
