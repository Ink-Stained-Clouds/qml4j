package io.qml4j.android;

import android.content.Context;
import android.opengl.GLSurfaceView;

import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.QmlView;
import io.qml4j.render.SurfaceBackend;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class QmlGLSurfaceView extends GLSurfaceView {

    private final String qmlSource;
    private final QmlEngine engine;
    private QmlView view;
    private final io.qml4j.render.Renderer renderer = new io.qml4j.render.Renderer();
    private SkijaGlSurface surface;

    public QmlGLSurfaceView(Context ctx, QmlEngine engine, String qmlSource) {
        super(ctx);
        this.engine = engine;
        this.qmlSource = qmlSource;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 8);
        setRenderer(new GlRenderer());
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    private final class GlRenderer implements GLSurfaceView.Renderer {
        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            surface = new SkijaGlSurface();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            surface.resize(width, height);
            if (view == null) {
                view = QmlView.withStockTypes(engine);
                view.load(qmlSource);
            }
            if (view.root() != null) {
                view.root().width.set(width);
                view.root().height.set(height);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (view == null) return;
            Canvas canvas = surface.acquireCanvas();
            renderer.render(canvas, view.root());
            surface.present();
        }
    }

    private static final class SkijaGlSurface implements SurfaceBackend {
        private DirectContext context;
        private BackendRenderTarget target;
        private Surface surface;
        private int width, height;

        @Override
        public void init(int w, int h) {
            this.width = w;
            this.height = h;
            context = DirectContext.makeGL();
            rebuild();
        }

        @Override
        public Canvas acquireCanvas() {
            return surface.getCanvas();
        }

        @Override
        public void present() {
            context.flush();
        }

        @Override
        public void resize(int w, int h) {
            if (context == null) {
                init(w, h);
                return;
            }
            if (w == width && h == height) return;
            this.width = w;
            this.height = h;
            rebuild();
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

        private void rebuild() {
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
}
