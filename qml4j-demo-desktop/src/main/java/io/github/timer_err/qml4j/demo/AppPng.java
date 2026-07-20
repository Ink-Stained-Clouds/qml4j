package io.github.timer_err.qml4j.demo;

import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class AppPng {
    // Dev screenshot tool: raster Surfaces live for the process lifetime; skija encode APIs are
    // deprecated-but-functional -- not worth migrating a throwaway tool.
    @SuppressWarnings({"deprecation", "resource"})
    public static void main(String[] a) throws Exception {
        String entry = a.length>0?a[0]:"pages/NavigationPage.qml";
        int w = a.length>1?Integer.parseInt(a[1]):1280, h = a.length>2?Integer.parseInt(a[2]):800;
        boolean dark = a.length>3 && a[3].equals("dark");
        boolean noset = a.length>4 && a[4].equals("noset");
        if (!noset) ((io.github.timer_err.qml4j.runtime.color.StyleManager)io.github.timer_err.qml4j.runtime.color.StyleManager.__instance()).isDarkTheme.set(dark);
        AppResourceLoader loader = new AppResourceLoader();
        QmlView v = QmlView.withStockTypes(new QmlEngine()).resources(loader);
        v.context("AppFeatures", AppFeaturesMap.all());
        v.context("HotReloadEnabled", Boolean.FALSE);
        v.context("ProjectSourceDir", "");
        byte[] b = loader.load(entry);

        int slash = entry.lastIndexOf('/');
        String baseDir = slash < 0 ? "" : entry.substring(0, slash);
        try { v.load(new String(b, StandardCharsets.UTF_8), baseDir); }
        catch (Throwable t) { System.out.println("LOAD FAIL"); t.printStackTrace(System.out); return; }
        v.root().x.set(0); v.root().y.set(0); v.root().width.set(w); v.root().height.set(h);
        Surface s = Surface.makeRasterN32Premul(w,h);
        AppShotBackend bk = new AppShotBackend(s,w,h);
        for (int i=0;i<40;i++){ s.getCanvas().clear(0x00000000); v.renderFrame(bk); Thread.sleep(12); }
        Surface flat = Surface.makeRasterN32Premul(w,h);
        flat.getCanvas().clear(dark?0xFF1c1b20:0xFFffffff);
        s.draw(flat.getCanvas(),0,0,null);
        Files.write(Paths.get("/tmp/page-"+entry.replace('/','_')+".png"),
            flat.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG).getBytes());
        System.out.println("wrote /tmp/page-"+entry.replace('/','_')+".png");
    }
    static final class AppShotBackend implements SurfaceBackend {
        final Surface s; int w,h; AppShotBackend(Surface s,int w,int h){this.s=s;this.w=w;this.h=h;}
        public void init(int w,int h){} public Canvas acquireCanvas(){return s.getCanvas();}
        public void present(){} public void resize(int w,int h){this.w=w;this.h=h;}
        public void dispose(){} public int width(){return w;} public int height(){return h;}
    }
}
