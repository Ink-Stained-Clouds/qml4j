package io.qml4j.android;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.QmlView;
import io.qml4j.render.ResourceLoader;
import io.qml4j.render.SurfaceBackend;
import io.qml4j.render.items.TextInput;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class QmlGLSurfaceView extends GLSurfaceView {

    private final String qmlSource;
    private final QmlEngine engine;
    private final ResourceLoader resources;
    private QmlView view;
    // FQN: GLSurfaceView.Renderer (inherited) shadows imported Renderer in a subclass.
    private final io.qml4j.render.Renderer renderer = new io.qml4j.render.Renderer();
    private SkijaGlSurface surface;

    public QmlGLSurfaceView(Context ctx, QmlEngine engine, String qmlSource, ResourceLoader resources) {
        super(ctx);
        this.engine = engine;
        this.qmlSource = qmlSource;
        this.resources = resources;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 8);
        setRenderer(new GlRenderer());
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        final float x = ev.getX();
        final float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                requestFocus();
                queueEvent(new Runnable() {
                    @Override public void run() {
                        if (view == null) return;
                        boolean hitTextInput = view.pickTextInput(x, y) != null;
                        view.dispatchPointerDown(x, y);
                        if (hitTextInput) showImeOnUiThread();
                    }
                });
                return true;
            case MotionEvent.ACTION_MOVE:
                queueEvent(new Runnable() {
                    @Override public void run() {
                        if (view != null) view.dispatchPointerMove(x, y);
                    }
                });
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                queueEvent(new Runnable() {
                    @Override public void run() {
                        if (view != null) view.dispatchPointerUp(x, y);
                    }
                });
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return view != null && view.focused() instanceof TextInput;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        if (view != null && view.focused() instanceof TextInput) {
            TextInput ti = (TextInput) view.focused();
            int pos = ti.cursorPosition.peek().intValue();
            outAttrs.initialSelStart = pos;
            outAttrs.initialSelEnd = pos;
        }
        return new QmlInputConnection(this, true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (dispatchKeyEvent(event, true)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (dispatchKeyEvent(event, false)) return true;
        return super.onKeyUp(keyCode, event);
    }

    boolean dispatchKeyEvent(KeyEvent event, boolean down) {
        if (view == null) return false;
        if (!(view.focused() instanceof TextInput)) return false;
        final int mapped = mapKeyCode(event.getKeyCode());
        final String text;
        if (mapped == 0) {
            int unicode = event.getUnicodeChar();
            if (unicode == 0) return false;
            text = new String(Character.toChars(unicode));
        } else {
            text = null;
        }
        final boolean isDown = down;
        final boolean shift = event.isShiftPressed();
        queueEvent(new Runnable() {
            @Override public void run() {
                if (view != null) view.dispatchKey(mapped, text, isDown, shift);
            }
        });
        return true;
    }

    void commitTextFromIme(final CharSequence text) {
        if (text == null) return;
        final String s = text.toString();
        if (s.isEmpty()) return;
        queueEvent(new Runnable() {
            @Override public void run() {
                if (view != null) view.dispatchKey(0, s, true);
            }
        });
    }

    QmlView qmlView() {
        return view;
    }

    void deleteFromIme(final int beforeLength) {
        if (beforeLength <= 0) return;
        queueEvent(new Runnable() {
            @Override public void run() {
                if (view == null) return;
                for (int i = 0; i < beforeLength; i++) {
                    view.dispatchKey(QmlView.KEY_BACKSPACE, null, true);
                }
            }
        });
    }

    void performImeEnter() {
        queueEvent(new Runnable() {
            @Override public void run() {
                if (view != null) view.dispatchKey(QmlView.KEY_ENTER, null, true);
            }
        });
    }

    private static int mapKeyCode(int kc) {
        if (kc == KeyEvent.KEYCODE_DEL) return QmlView.KEY_BACKSPACE;
        if (kc == KeyEvent.KEYCODE_ENTER || kc == KeyEvent.KEYCODE_NUMPAD_ENTER) return QmlView.KEY_ENTER;
        if (kc == KeyEvent.KEYCODE_DPAD_LEFT) return QmlView.KEY_LEFT;
        if (kc == KeyEvent.KEYCODE_DPAD_RIGHT) return QmlView.KEY_RIGHT;
        if (kc == KeyEvent.KEYCODE_MOVE_HOME) return QmlView.KEY_HOME;
        if (kc == KeyEvent.KEYCODE_MOVE_END) return QmlView.KEY_END;
        return 0;
    }

    private void hideImeOnUiThread() {
        runOnUi(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = imm();
                if (imm == null) return;
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        });
    }

    private void showImeOnUiThread() {
        runOnUi(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = imm();
                if (imm == null) return;
                requestFocus();
                imm.restartInput(QmlGLSurfaceView.this);
                imm.showSoftInput(QmlGLSurfaceView.this, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private InputMethodManager imm() {
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    private void runOnUi(Runnable r) {
        Context ctx = getContext();
        if (ctx instanceof Activity) ((Activity) ctx).runOnUiThread(r);
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
                view = QmlView.withStockTypes(engine).resources(resources);
                view.setFocusListener((nf, of) -> {
                    if (of instanceof TextInput && !(nf instanceof TextInput)) {
                        hideImeOnUiThread();
                    }
                });
                view.load(qmlSource);
                renderer.setResourceLoader(resources);
            }
            if (view.root() != null) {
                view.root().width.set(width);
                view.root().height.set(height);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (view == null) return;
            DirtyQueue dq = view.dirtyQueue();
            dq.install();
            try {
                view.tickAnimations(System.nanoTime());
                dq.flush();
                Canvas canvas = surface.acquireCanvas();
                renderer.render(canvas, view.root());
                surface.present();
            } finally {
                dq.uninstall();
            }
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
