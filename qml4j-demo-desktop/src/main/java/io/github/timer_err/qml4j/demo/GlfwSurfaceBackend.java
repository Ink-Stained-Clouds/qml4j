package io.github.timer_err.qml4j.demo;

import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.timer_err.qml4j.render.SurfaceBackend;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

public final class GlfwSurfaceBackend implements SurfaceBackend {

    private final long window;
    private int width;
    private int height;
    private DirectContext context;
    private BackendRenderTarget target;
    private Surface surface;

    public GlfwSurfaceBackend(long window, int width, int height) {
        this.window = window;
        this.width = width;
        this.height = height;
    }

    @Override
    public void init(int w, int h) {
        this.width = w;
        this.height = h;
        GL.createCapabilities();
        context = DirectContext.makeGL();
        rebuildSurface();
    }

    @Override
    public Canvas acquireCanvas() {
        // Clear through Skija, not a raw GL11.glClear: a bare glClear is invisible
        // to Skija's DirectContext and drops the frame's first draw (the root's
        // full-surface fill), leaving the background black. canvas.clear() enters
        // Skija's own command stream so ordering is correct.
        Canvas canvas = surface.getCanvas();
        canvas.clear(0xFF000000);
        return canvas;
    }

    @Override
    public DirectContext recordingContext() {
        return context;
    }

    @Override
    public void present() {
        context.flush();
        GLFW.glfwSwapBuffers(window);
    }

    @Override
    public void resize(int w, int h) {
        if (w == width && h == height) return;
        this.width = w;
        this.height = h;
        rebuildSurface();
    }

    @Override
    public int width() { return width; }

    @Override
    public int height() { return height; }

    @Override
    public void dispose() {
        if (surface != null) { surface.close(); surface = null; }
        if (target != null) { target.close(); target = null; }
        if (context != null) { context.close(); context = null; }
    }

    private void rebuildSurface() {
        if (surface != null) surface.close();
        if (target != null) target.close();
        target = BackendRenderTarget.makeGL(width, height, 0, 8, 0,
                                            FramebufferFormat.GR_GL_RGBA8);
        surface = Surface.makeFromBackendRenderTarget(
            context, target,
            SurfaceOrigin.BOTTOM_LEFT,
            ColorType.RGBA_8888,
            ColorSpace.getSRGB());
    }
}
