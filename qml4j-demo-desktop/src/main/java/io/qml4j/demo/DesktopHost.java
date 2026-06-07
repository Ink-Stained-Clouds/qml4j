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

    // Run the upstream MD3 app (src/App/Main.qml) instead of a showcase: its own
    // resource loader + the host context properties, started in the dark scheme.
    void startApp() {
        AppResourceLoader appLoader = new AppResourceLoader();
        byte[] bytes = appLoader.load("Main.qml");
        if (bytes == null) {
            setView(errorQml("App", new IllegalStateException("Main.qml not found at /tmp/mcq")));
            return;
        }
        inLauncher = false;
        ((io.qml4j.runtime.color.StyleManager) io.qml4j.runtime.color.StyleManager.__instance())
            .isDarkTheme.set(Boolean.TRUE);
        if (view != null) view.dispose();
        QmlEngine engine = new QmlEngine();
        view = QmlView.withStockTypes(engine).resources(appLoader);
        view.setClipboard(new AwtClipboard());
        view.context("AppFeatures", new java.util.HashMap<String, Object>());
        view.context("HotReloadEnabled", Boolean.FALSE);
        view.context("ProjectSourceDir", "");
        try {
            view.load(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            setView(errorQml("App", e));
            return;
        }
        sizeRoot();
    }

    private void openShowcase(int index) {
        Showcase sc = showcases.get(index);
        byte[] bytes = loader.load(sc.resource);
        if (bytes == null) return;
        inLauncher = false;
        // A showcase is untrusted content (it may reference a type the engine doesn't
        // provide); a compile/load failure shows an error page instead of taking the
        // whole host down. Esc still returns to the launcher.
        try {
            setView(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            setView(errorQml(sc.title, e));
        }
    }

    // No \n escapes in the generated string: the error page must never itself fail
    // to compile, so it sticks to plain literals and a wrapped Text for the message.
    private static String errorQml(String title, Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        msg = msg.split("\n")[0].replace("\"", "'").replace("\\", "/");
        return "import QtQuick\n"
            + "Rectangle { x: 0; y: 0; color: \"#3b1f1f\"\n"
            + "  Text { x: 24; y: 24; color: \"#ffd0d0\"; fontSize: 20;\n"
            + "    text: \"" + title + " failed to load (Esc to return):\" }\n"
            + "  Text { x: 24; y: 64; width: parent.width - 48; wrapMode: Text.WordWrap;\n"
            + "    color: \"#ffb0b0\"; fontSize: 15; text: \"" + msg + "\" }\n"
            + "}\n";
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
        // Pin the root to the viewport origin: several showcases were sliced out of a
        // long scrolling demo page and kept their original y offset (e.g. y: 9240),
        // which would otherwise paint their content far off-screen.
        view.root().x.set(0);
        view.root().y.set(0);
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
