package io.github.timer_err.qml4j.compat;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.ResourceLoader;
import io.github.timer_err.qml4j.render.SurfaceBackend;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.runtime.color.StyleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Headless render/flush helpers for compatibility E2E. Writes PNGs under
// target/e2e-shots/ so a human can open light/dark pairs side-by-side.
// Requires Skija natives on the test classpath (linux-x64 in CI; windows-x64
// profile on this machine).
public final class OffscreenCompat {

    private OffscreenCompat() {}

    public static StyleManager styleManager() {
        return (StyleManager) StyleManager.__instance();
    }

    public static QmlView view(ResourceLoader resources) {
        return QmlView.withStockTypes(new QmlEngine()).resources(resources);
    }

    public static void flush(QmlView v) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            dq.flush();
        } finally {
            dq.uninstall();
        }
    }

    public static void setTheme(boolean dark, String seedOrNull) {
        StyleManager sm = styleManager();
        sm.isDarkTheme.set(dark);
        if (seedOrNull != null) sm.seedColor.set(seedOrNull);
    }

    // Renders `frames` settle passes and writes a PNG. Returns the absolute path.
    public static Path shot(QmlView v, int w, int h, boolean darkBg, int frames, Path out) throws Exception {
        Item root = v.root();
        root.x.set(0);
        root.y.set(0);
        root.width.set(w);
        root.height.set(h);
        Surface s = Surface.makeRasterN32Premul(Math.max(1, w), Math.max(1, h));
        ShotBackend bk = new ShotBackend(s, Math.max(1, w), Math.max(1, h));
        for (int i = 0; i < frames; i++) {
            s.getCanvas().clear(0x00000000);
            flush(v);
            v.renderFrame(bk);
            Thread.sleep(8);
        }
        Surface flat = Surface.makeRasterN32Premul(Math.max(1, w), Math.max(1, h));
        flat.getCanvas().clear(darkBg ? 0xFF141218 : 0xFFFEF7FF);
        s.draw(flat.getCanvas(), 0, 0, null);
        Files.createDirectories(out.getParent());
        Files.write(out, flat.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG).getBytes());
        s.close();
        flat.close();
        return out.toAbsolutePath();
    }

    // Mean absolute channel delta across the two PNGs (0 = identical). Used to
    // assert a theme flip actually recolored the scene, not just that a file exists.
    public static double meanAbsDelta(Path a, Path b) throws Exception {
        Image ima = Image.makeDeferredFromEncodedBytes(Files.readAllBytes(a));
        Image imb = Image.makeDeferredFromEncodedBytes(Files.readAllBytes(b));
        try {
            if (ima.getWidth() != imb.getWidth() || ima.getHeight() != imb.getHeight()) {
                return Double.POSITIVE_INFINITY;
            }
            Bitmap bma = new Bitmap();
            Bitmap bmb = new Bitmap();
            bma.allocPixels(ima.getImageInfo());
            bmb.allocPixels(imb.getImageInfo());
            ima.readPixels(bma);
            imb.readPixels(bmb);
            int w = ima.getWidth();
            int h = ima.getHeight();
            long sum = 0;
            int samples = 0;
            // Sample a grid -- enough for theme recolor detection, cheap enough for CI.
            for (int y = 0; y < h; y += 4) {
                for (int x = 0; x < w; x += 4) {
                    int pa = bma.getColor(x, y);
                    int pb = bmb.getColor(x, y);
                    sum += Math.abs(((pa >> 16) & 0xff) - ((pb >> 16) & 0xff));
                    sum += Math.abs(((pa >> 8) & 0xff) - ((pb >> 8) & 0xff));
                    sum += Math.abs((pa & 0xff) - (pb & 0xff));
                    samples++;
                }
            }
            bma.close();
            bmb.close();
            return samples == 0 ? 0 : (sum / 3.0) / samples;
        } finally {
            ima.close();
            imb.close();
        }
    }

    public static List<Item> flatten(Item root) {
        List<Item> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    private static void walk(Item n, List<Item> out) {
        if (n == null) return;
        out.add(n);
        if (n.children == null) return;
        for (Item c : n.children) walk(c, out);
    }

    static final class ShotBackend implements SurfaceBackend {
        final Surface s;
        int w, h;

        ShotBackend(Surface s, int w, int h) {
            this.s = s;
            this.w = w;
            this.h = h;
        }

        @Override public void init(int w, int h) {}
        @Override public Canvas acquireCanvas() { return s.getCanvas(); }
        @Override public void present() {}
        @Override public void resize(int w, int h) { this.w = w; this.h = h; }
        @Override public void dispose() {}
        @Override public int width() { return w; }
        @Override public int height() { return h; }
    }
}
