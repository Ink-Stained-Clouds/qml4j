package io.qml4j.demo;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.QmlView;
import io.qml4j.render.ResourceLoader;
import io.qml4j.render.SurfaceBackend;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Owns the live QmlView and routes already-framebuffer-scaled input into it.
// The launcher and the showcases are separate screens; switching tears down the
// old view (fresh QmlEngine per screen, mirroring the Android shell) and builds a
// new one. All coordinates are in framebuffer pixels, matching root width/height.
final class DesktopHost {

    private final ResourceLoader loader;
    private final List<Showcase> showcases;
    private final LauncherScreen launcher;

    private QmlView view;
    private boolean inLauncher;
    private int fbW;
    private int fbH;

    DesktopHost(ResourceLoader loader, int fbW, int fbH) {
        this.loader = loader;
        this.showcases = Showcase.all();
        this.launcher = new LauncherScreen(showcases);
        this.fbW = fbW;
        this.fbH = fbH;
    }

    // Optional startup shortcut: open the showcase whose resource matches `name`
    // (substring, case-insensitive) instead of the launcher. Esc still returns.
    void start(String name) {
        if (name != null) {
            for (int i = 0; i < showcases.size(); i++) {
                if (showcases.get(i).resource.toLowerCase().contains(name.toLowerCase())) {
                    openShowcase(i);
                    return;
                }
            }
        }
        showLauncher();
    }

    void showLauncher() {
        inLauncher = true;
        setView(launcher.qml());
    }

    private void openShowcase(int index) {
        byte[] bytes = loader.load(showcases.get(index).resource);
        if (bytes == null) return;
        inLauncher = false;
        setView(new String(bytes, StandardCharsets.UTF_8));
    }

    private void setView(String qml) {
        if (view != null) view.dispose();
        QmlEngine engine = new QmlEngine();
        view = QmlView.withStockTypes(engine).resources(loader);
        view.setClipboard(new AwtClipboard());
        view.load(qml);
        sizeRoot();
    }

    private void sizeRoot() {
        if (view.root() == null) return;
        view.root().width.set(fbW);
        view.root().height.set(fbH);
    }

    void resize(int w, int h) {
        fbW = w;
        fbH = h;
        if (view != null) sizeRoot();
    }

    void renderFrame(SurfaceBackend backend) {
        if (view != null) view.renderFrame(backend);
    }

    void pointerDown(float x, float y) {
        if (!inLauncher) view.dispatchPointerDown(x, y);
    }

    void pointerMove(float x, float y) {
        if (!inLauncher) view.dispatchPointerMove(x, y);
    }

    void pointerUp(float x, float y) {
        if (inLauncher) {
            int idx = launcher.indexAt(x, y);
            if (idx >= 0) openShowcase(idx);
            return;
        }
        view.dispatchPointerUp(x, y);
    }

    void key(int code, String text, boolean down, boolean shift) {
        if (down && code == QmlView.KEY_ESCAPE) {
            if (!inLauncher) showLauncher();
            return;
        }
        if (!inLauncher) view.dispatchKey(code, text, down, shift);
    }

    void text(String s) {
        if (!inLauncher && s != null && !s.isEmpty()) view.dispatchKey(0, s, true);
    }

    void dispose() {
        if (view != null) view.dispose();
    }
}
