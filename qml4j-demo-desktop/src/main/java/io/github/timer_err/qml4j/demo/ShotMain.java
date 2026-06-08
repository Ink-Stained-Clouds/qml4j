package io.github.timer_err.qml4j.demo;

import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.SurfaceBackend;
import io.github.timer_err.qml4j.runtime.color.StyleManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Headless raster of a single /showcases/*.qml to /tmp/shot-<name><tag>.png, for
// visual verification without a display. Sibling of AppPng (which renders the mcq app).
//   args: <name> [w] [h] [tag] [frames] [dark|light] [seedColor]
//   e.g. ShotMain FisProxyShowcase 1280 872 _light 30 light "#2196F3"
final class ShotMain {
    public static void main(String[] a) throws Exception {
        String name = a.length > 0 ? a[0] : "FisProxyShowcase";
        int w = a.length > 1 ? Integer.parseInt(a[1]) : 1280;
        int h = a.length > 2 ? Integer.parseInt(a[2]) : 872;
        String tag = a.length > 3 ? a[3] : "";
        int frames = a.length > 4 ? Integer.parseInt(a[4]) : 30;
        boolean dark = a.length > 5 ? a[5].equals("dark") : true;
        String seed = a.length > 6 ? a[6] : null;
        StyleManager sm = (StyleManager) StyleManager.__instance();
        sm.isDarkTheme.set(dark);
        if (seed != null) sm.seedColor.set(seed);
        DesktopResourceLoader loader = new DesktopResourceLoader();
        QmlView v = QmlView.withStockTypes(new QmlEngine()).resources(loader);
        byte[] b = loader.load("/showcases/" + name + ".qml");
        try { v.load(new String(b, StandardCharsets.UTF_8), "showcases"); }
        catch (Throwable t) { System.out.println("LOAD FAIL"); t.printStackTrace(System.out); return; }
        v.root().x.set(0); v.root().y.set(0); v.root().width.set(w); v.root().height.set(h);
        long bg = dark ? 0xFF141218L : 0xFFFEF7FFL;
        Surface s = Surface.makeRasterN32Premul(Math.max(1, w), Math.max(1, h));
        ShotBackend bk = new ShotBackend(s, Math.max(1, w), Math.max(1, h));
        for (int i = 0; i < frames; i++) { s.getCanvas().clear(0x00000000); v.renderFrame(bk); Thread.sleep(10); }
        Surface flat = Surface.makeRasterN32Premul(Math.max(1, w), Math.max(1, h));
        flat.getCanvas().clear((int) bg);
        s.draw(flat.getCanvas(), 0, 0, null);
        Path out = Paths.get("/tmp/shot-" + name + tag + ".png");
        Files.write(out, flat.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG).getBytes());
        System.out.println("wrote " + out);
    }
    static final class ShotBackend implements SurfaceBackend {
        final Surface s; int w, h;
        ShotBackend(Surface s, int w, int h) { this.s = s; this.w = w; this.h = h; }
        public void init(int w, int h) {}
        public Canvas acquireCanvas() { return s.getCanvas(); }
        public void present() {}
        public void resize(int w, int h) { this.w = w; this.h = h; }
        public void dispose() {}
        public int width() { return w; }
        public int height() { return h; }
    }
}
