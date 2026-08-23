package io.github.timer_err.qml4j.render;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Draw-phase content cache (per-boundary SkPicture reuse): the boundaries are the root's direct
// children (the MVP). Guards the two behavioural claims -- a pure-geometry animation re-records
// nothing, a local content change re-records only its own boundary -- plus pixel identity with
// the direct (uncached) paint path.
class PictureCacheTest {

    @AfterEach
    void resetGlobalFlag() {
        // The feature gate is process-wide (like the change version); leave it off for other tests.
        Item.setContentCacheEnabled(false);
    }

    // A minimal raster (CPU) surface backend so renderFrame drives the full paint path headless.
    // `scale` emulates a high-DPI host: the surface is allocated at device size and each acquired
    // canvas carries a device-scale CTM (reset per frame so scaling doesn't compound).
    private static final class RasterBackend implements SurfaceBackend {
        final Surface surface;
        final int w, h;
        final float scale;
        RasterBackend(int w, int h) { this(w, h, 1f); }
        RasterBackend(int w, int h, float scale) {
            this.scale = scale;
            this.w = Math.round(w * scale);
            this.h = Math.round(h * scale);
            this.surface = Surface.makeRasterN32Premul(this.w, this.h);
        }
        public void init(int w, int h) {}
        public Canvas acquireCanvas() {
            Canvas c = surface.getCanvas();
            c.resetMatrix();
            if (scale != 1f) c.scale(scale, scale);
            return c;
        }
        public void present() {}
        public void resize(int w, int h) {}
        public void dispose() { surface.close(); }
        public int width() { return w; }
        public int height() { return h; }
    }

    private static final String SCENE =
        "import QtQuick\n"
        + "Item {\n"
        + "  width: 300; height: 100\n"
        + "  Rectangle { objectName: \"p0\"; x: 0;   y: 0; width: 100; height: 100; color: \"#ff0000\"\n"
        + "    Rectangle { objectName: \"c0\"; x: 10; y: 10; width: 20; height: 20; color: \"#00ff00\" } }\n"
        + "  Rectangle { objectName: \"p1\"; x: 100; y: 0; width: 100; height: 100; color: \"#0000ff\" }\n"
        + "  Rectangle { objectName: \"p2\"; x: 200; y: 0; width: 100; height: 100; color: \"#ffff00\" }\n"
        + "}";

    private static QmlView loadCached() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(SCENE);
        root.width.set(300);
        root.height.set(100);
        v.renderer().setPictureCache(true);
        return v;
    }

    @Test
    void firstFrameRecordsEveryBoundaryOnce() {
        QmlView v = loadCached();
        RasterBackend bk = new RasterBackend(300, 100);
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);

        Item root = v.root();
        assertEquals(3, root.children.size(), "three panels are the boundaries");
        for (Item panel : root.children) {
            assertTrue(panel.cacheBoundary, "root's direct child is a boundary");
            assertEquals(1, panel.recordCount, "recorded exactly once on first appearance");
            assertFalse(panel.contentDirty, "clean after recording");
        }
        assertEquals(3, v.renderer().pictureRecordsTotal());
    }

    @Test
    void pureGeometryDragReRecordsNothing() {
        QmlView v = loadCached();
        RasterBackend bk = new RasterBackend(300, 100);
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);              // warm: records all three

        Item p1 = v.findByObjectName("p1");
        for (int i = 0; i < 5; i++) {
            p1.x.set(100.0 + i);        // drag the whole panel: x only
            bk.surface.getCanvas().clear(0);
            v.renderFrame(bk);
            assertEquals(0, v.renderer().pictureRecordsThisFrame(),
                "moving a whole boundary replays its picture, never re-records");
        }
        assertEquals(1, p1.recordCount, "still the single warm-up record");
        assertEquals(3, v.renderer().pictureRecordsTotal());
    }

    @Test
    void localContentChangeReRecordsOnlyItsBoundary() {
        QmlView v = loadCached();
        RasterBackend bk = new RasterBackend(300, 100);
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);              // warm

        // Change a DESCENDANT inside p0 -- bubbles to the p0 boundary only.
        Rectangle c0 = (Rectangle) v.findByObjectName("c0");
        c0.color.set("#000000");
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);

        Item p0 = v.findByObjectName("p0");
        Item p1 = v.findByObjectName("p1");
        Item p2 = v.findByObjectName("p2");
        assertEquals(1, v.renderer().pictureRecordsThisFrame(), "only one boundary re-recorded");
        assertEquals(2, p0.recordCount, "p0 re-recorded (its descendant changed)");
        assertEquals(1, p1.recordCount, "p1 untouched");
        assertEquals(1, p2.recordCount, "p2 untouched");
    }

    // The cached path must produce pixel-identical output to the direct (uncached) paint path.
    @Test
    void cachedPixelsMatchDirectPaint() {
        // Reference: same scene, cache OFF.
        QmlView ref = QmlView.withStockTypes(new QmlEngine());
        Item refRoot = ref.load(SCENE);
        refRoot.width.set(300);
        refRoot.height.set(100);
        ref.renderer().setPictureCache(false);
        RasterBackend refBk = new RasterBackend(300, 100);
        refBk.surface.getCanvas().clear(0xFF202020);
        ref.renderFrame(refBk);
        byte[] refPx = snapshot(refBk.surface);

        // Cached: same scene, cache ON, a couple of frames so pictures are recorded + replayed.
        QmlView cached = loadCached();
        RasterBackend cachedBk = new RasterBackend(300, 100);
        cachedBk.surface.getCanvas().clear(0xFF202020);
        cached.renderFrame(cachedBk);
        cachedBk.surface.getCanvas().clear(0xFF202020);
        cached.renderFrame(cachedBk);   // second frame replays from the picture
        byte[] cachedPx = snapshot(cachedBk.surface);

        assertTrue(cached.renderer().pictureRecordsTotal() >= 3, "did record via the cached path");
        assertArrayEquals(refPx, cachedPx, "cached replay is pixel-identical to direct paint");
    }

    // Dragging a whole panel (cache ON) still lands the panel at its new position, pixel-for-pixel
    // with the direct path -- proves the replay uses the LIVE translate, not the baked one.
    @Test
    void draggedBoundaryPixelsMatchDirectPaint() {
        QmlView cached = loadCached();
        RasterBackend cachedBk = new RasterBackend(300, 100);
        cachedBk.surface.getCanvas().clear(0xFF202020);
        cached.renderFrame(cachedBk);
        Item p1c = cached.findByObjectName("p1");
        p1c.x.set(130.0);
        cachedBk.surface.getCanvas().clear(0xFF202020);
        cached.renderFrame(cachedBk);
        byte[] cachedPx = snapshot(cachedBk.surface);
        assertEquals(0, cached.renderer().pictureRecordsThisFrame(), "drag replayed, no re-record");

        QmlView ref = QmlView.withStockTypes(new QmlEngine());
        Item refRoot = ref.load(SCENE);
        refRoot.width.set(300);
        refRoot.height.set(100);
        ref.renderer().setPictureCache(false);
        ref.findByObjectName("p1").x.set(130.0);
        RasterBackend refBk = new RasterBackend(300, 100);
        refBk.surface.getCanvas().clear(0xFF202020);
        ref.renderFrame(refBk);
        byte[] refPx = snapshot(refBk.surface);

        assertArrayEquals(refPx, cachedPx, "dragged panel matches direct paint at the new position");
    }

    // A boundary whose content changes every frame is a hot spot: after a few frames the renderer
    // stops recording (which would cost a record + a replay) and draws it directly, then resumes
    // caching once the content settles.
    @Test
    void continuouslyDirtyBoundaryStopsRecording() {
        QmlView v = loadCached();
        RasterBackend bk = new RasterBackend(300, 100);
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);              // warm: p0 recorded once

        Rectangle c0 = (Rectangle) v.findByObjectName("c0");
        // Animate a descendant of p0 for many frames.
        for (int i = 0; i < 12; i++) {
            c0.color.set(i % 2 == 0 ? "#010101" : "#020202");
            bk.surface.getCanvas().clear(0);
            v.renderFrame(bk);
        }
        Item p0 = v.findByObjectName("p0");
        Item p1 = v.findByObjectName("p1");
        assertTrue(p0.recordCount <= 3,
            "hot boundary stops recording, got recordCount=" + p0.recordCount);
        assertEquals(0, v.renderer().pictureRecordsThisFrame(), "last hot frame drew directly");
        assertEquals(1, p1.recordCount, "the static panel never re-recorded");

        // Settle: stop changing p0. One clean frame resets the streak; the next records once more.
        int before = p0.recordCount;
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);              // clean frame -> streak resets, re-records the settled panel
        assertEquals(before + 1, p0.recordCount, "resumed caching after settle");
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);              // now replays
        assertEquals(0, v.renderer().pictureRecordsThisFrame(), "settled panel replays, no record");
    }

    // A paint-only property on a nested widget (Button.down -- changes the drawn colour, no
    // geometry) must still invalidate the enclosing boundary. Guards the subclass paint wiring.
    @Test
    void paintOnlyWidgetChangeReRecords() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "import QtQuick.Controls\n"
            + "Item {\n"
            + "  width: 200; height: 100\n"
            + "  Rectangle { objectName: \"panel\"; width: 200; height: 100; color: \"#222222\"\n"
            + "    Button { objectName: \"btn\"; x: 10; y: 10; width: 80; height: 30; text: \"ok\" } }\n"
            + "}");
        root.width.set(200);
        root.height.set(100);
        v.renderer().setPictureCache(true);
        RasterBackend bk = new RasterBackend(200, 100);
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);

        Item panel = v.findByObjectName("panel");
        int before = panel.recordCount;
        io.github.timer_err.qml4j.render.items.window.Button btn =
            (io.github.timer_err.qml4j.render.items.window.Button) v.findByObjectName("btn");
        btn.down.set(Boolean.TRUE);   // pressed colour: pure paint change, no layout
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);

        assertEquals(before + 1, panel.recordCount, "nested widget's paint change re-records boundary");
        assertEquals(1, v.renderer().pictureRecordsThisFrame());
    }

    // A nested value holder (a Gradient stop colour -- a QObject, not an Item) changing must
    // still invalidate the enclosing boundary. Guards the reflective holder wiring.
    @Test
    void gradientStopChangeReRecords() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  width: 200; height: 100\n"
            + "  Rectangle { objectName: \"panel\"; width: 200; height: 100; color: \"#111111\"\n"
            + "    Rectangle { objectName: \"grad\"; x: 10; y: 10; width: 50; height: 50\n"
            + "      gradient: Gradient {\n"
            + "        GradientStop { position: 0; color: \"#ff0000\" }\n"
            + "        GradientStop { position: 1; color: \"#0000ff\" } } } }\n"
            + "}");
        root.width.set(200);
        root.height.set(100);
        v.renderer().setPictureCache(true);
        RasterBackend bk = new RasterBackend(200, 100);
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);

        Item panel = v.findByObjectName("panel");
        int before = panel.recordCount;
        io.github.timer_err.qml4j.render.items.core.Rectangle grad =
            (io.github.timer_err.qml4j.render.items.core.Rectangle) v.findByObjectName("grad");
        grad.gradient.peek().stops.get(0).color.set("#00ff00");   // animate a stop colour
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);

        assertEquals(before + 1, panel.recordCount, "gradient stop change re-records the boundary");
    }

    // The first cached picture can be recorded while an Image worker is still decoding. The
    // worker's completion must invalidate that picture itself instead of relying on a later
    // scroll/hover/property change to make the cover appear.
    @Test
    void asyncImageCompletionInvalidatesCachedBoundary() throws Exception {
        byte[] png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2jXkAAAAASUVORK5CYII=");
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        QmlView v = QmlView.withStockTypes(new QmlEngine()).resources(source -> {
            loadStarted.countDown();
            try {
                if (!releaseLoad.await(2, TimeUnit.SECONDS)) return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            return png;
        });
        Item root = v.load(
            "import QtQuick\n"
            + "Item { width: 80; height: 80\n"
            + "  Rectangle { objectName: \"panel\"; width: 80; height: 80; color: \"#222222\"\n"
            + "    Image { objectName: \"cover\"; width: 64; height: 64; source: \"cover.png\" } }\n"
            + "}");
        root.width.set(80);
        root.height.set(80);
        v.renderer().setPictureCache(true);
        RasterBackend bk = new RasterBackend(80, 80);

        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk); // records the boundary while the worker is deliberately blocked
        assertTrue(loadStarted.await(1, TimeUnit.SECONDS), "image load started");
        Item panel = v.findByObjectName("panel");
        assertEquals(1, panel.recordCount);

        releaseLoad.countDown();
        io.github.timer_err.qml4j.render.items.core.Image cover =
            (io.github.timer_err.qml4j.render.items.core.Image) v.findByObjectName("cover");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (cover.status.peek().intValue() != 1 && System.nanoTime() < deadline) {
            Thread.sleep(5);
            bk.surface.getCanvas().clear(0);
            v.renderFrame(bk);
        }

        assertEquals(1, cover.status.peek().intValue(), "pending image was adopted without scrolling");
        assertTrue(panel.recordCount >= 2, "completion re-recorded the cached boundary");
        v.dispose();
        bk.dispose();
    }

    // Cached pictures are native memory; disposing an item (or the whole view) must close them.
    @Test
    void disposeReleasesCachedPictures() {
        QmlView v = loadCached();
        RasterBackend bk = new RasterBackend(300, 100);
        bk.surface.getCanvas().clear(0);
        v.renderFrame(bk);

        Item p1 = v.findByObjectName("p1");
        assertNotNull(p1.cachedPicture, "boundary recorded a picture");
        p1.dispose();
        assertNull(p1.cachedPicture, "disposing an item closes its cached picture");
        assertFalse(p1.cacheBoundary);

        Item p2 = v.findByObjectName("p2");
        assertNotNull(p2.cachedPicture);
        v.dispose();                     // renderer.dispose -> clearBoundaries closes the rest
        assertNull(p2.cachedPicture, "disposing the view closes remaining boundary pictures");
        assertFalse(p2.cacheBoundary);
    }

    // At high DPI the boundary picture is recorded at device scale (so raster backings stay
    // crisp), and a scale change forces one re-record.
    @Test
    void deviceScaleRecordedAndInvalidatesOnScaleChange() {
        QmlView v = loadCached();
        RasterBackend bk3 = new RasterBackend(300, 100, 3f);
        bk3.surface.getCanvas().clear(0);
        v.renderFrame(bk3);

        Item p0 = v.findByObjectName("p0");
        assertEquals(3f, p0.cachedScale, 1e-4, "picture recorded at the device scale");
        int rc = p0.recordCount;

        bk3.surface.getCanvas().clear(0);
        v.renderFrame(bk3);
        assertEquals(rc, p0.recordCount, "same scale replays, no re-record");

        // Render the same view at a different device scale -> one forced re-record at the new sf.
        RasterBackend bk2 = new RasterBackend(300, 100, 2f);
        bk2.surface.getCanvas().clear(0);
        v.renderFrame(bk2);
        assertEquals(2f, p0.cachedScale, 1e-4, "re-recorded at the new device scale");
        assertEquals(rc + 1, p0.recordCount, "scale change forces exactly one re-record");
    }

    // The device-scale pre-record/replay wrapping must be pixel-exact for vector content: cached
    // replay at 3x matches the direct paint path at 3x.
    @Test
    void highDpiVectorPixelsMatchDirectPaint() {
        QmlView ref = QmlView.withStockTypes(new QmlEngine());
        Item refRoot = ref.load(SCENE);
        refRoot.width.set(300);
        refRoot.height.set(100);
        ref.renderer().setPictureCache(false);
        RasterBackend refBk = new RasterBackend(300, 100, 3f);
        refBk.surface.getCanvas().clear(0xFF202020);
        ref.renderFrame(refBk);
        byte[] refPx = snapshot(refBk.surface);

        QmlView cached = loadCached();
        RasterBackend cachedBk = new RasterBackend(300, 100, 3f);
        cachedBk.surface.getCanvas().clear(0xFF202020);
        cached.renderFrame(cachedBk);
        cachedBk.surface.getCanvas().clear(0xFF202020);
        cached.renderFrame(cachedBk);   // second frame replays the device-scaled picture
        byte[] cachedPx = snapshot(cachedBk.surface);

        assertTrue(cached.renderer().pictureRecordsTotal() >= 3, "recorded via the cached path");
        assertArrayEquals(refPx, cachedPx, "high-DPI cached replay is pixel-identical to direct paint");
    }

    private static byte[] snapshot(Surface s) {
        try (Bitmap bm = Bitmap.makeFromImage(s.makeImageSnapshot())) {
            byte[] px = bm.readPixels();
            return px == null ? new byte[0] : Arrays.copyOf(px, px.length);
        }
    }
}
