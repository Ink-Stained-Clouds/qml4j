package io.qml4j.render;

import io.github.humbleui.skija.Canvas;

public interface SurfaceBackend {
    void init(int width, int height);
    Canvas acquireCanvas();
    void present();
    void resize(int width, int height);
    void dispose();
    int width();
    int height();
}
