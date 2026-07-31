package io.github.timer_err.qml4j.render;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Surface;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Rectangle's per-corner radii must round the rectangle's OWN fill, not only a mask it is used as.
//
// The four properties and cornerRadius() were consumed exclusively by the two mask paths
// (Renderer.maskClipRadii and Painter's MultiEffect mask clip), so a Rectangle asked to round one
// corner painted itself with the uniform `radius` instead: setting topLeftRadius alone changed
// nothing at all, and setting `radius` plus a squared-off corner rounded that corner anyway. Qt
// treats these as properties of the rectangle, not of masking.
class RectanglePerCornerRadiusTest {

    private static final class RasterBackend implements SurfaceBackend {
        final Surface surface;
        final int w, h;
        RasterBackend(int w, int h) {
            this.w = w;
            this.h = h;
            this.surface = Surface.makeRasterN32Premul(w, h);
        }
        public void init(int w, int h) {}
        public Canvas acquireCanvas() {
            Canvas c = surface.getCanvas();
            c.resetMatrix();
            return c;
        }
        public void present() {}
        public void resize(int w, int h) {}
        public void dispose() { surface.close(); }
        public int width() { return w; }
        public int height() { return h; }
    }

    private static Bitmap render(String scene, int w, int h) {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(scene);
        root.width.set(w);
        root.height.set(h);
        RasterBackend bk = new RasterBackend(w, h);
        bk.surface.getCanvas().clear(0xFF000000);
        v.renderFrame(bk);
        Bitmap out = new Bitmap();
        out.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL));
        bk.surface.readPixels(out, 0, 0);
        return out;
    }

    /** True when the pixel one step inside a corner has been cut away (i.e. that corner is round). */
    private static boolean cornerIsRounded(Bitmap bmp, int x, int y) {
        // The fill is opaque white on black; a rounded corner leaves the extreme pixel black.
        return (bmp.getColor(x, y) & 0x00FFFFFF) == 0;
    }

    private static final String ONE_CORNER =
        "import QtQuick\n"
        + "Item {\n"
        + "  width: 60; height: 60\n"
        + "  Rectangle { x: 0; y: 0; width: 60; height: 60; color: \"#ffffff\"\n"
        + "              topLeftRadius: 20 }\n"
        + "}";

    @Test
    void aSingleCornerRadiusRoundsThatCornerAndLeavesTheOthersSquare() {
        Bitmap bmp = render(ONE_CORNER, 60, 60);
        try {
            assertTrue(cornerIsRounded(bmp, 1, 1),
                "topLeftRadius: 20 must round the top-left corner of the rectangle's own fill");
            assertTrue(!cornerIsRounded(bmp, 58, 1), "top-right must stay square");
            assertTrue(!cornerIsRounded(bmp, 1, 58), "bottom-left must stay square");
            assertTrue(!cornerIsRounded(bmp, 58, 58), "bottom-right must stay square");
        } finally {
            bmp.close();
        }
    }

    private static final String SQUARED_ONE =
        "import QtQuick\n"
        + "Item {\n"
        + "  width: 60; height: 60\n"
        + "  Rectangle { x: 0; y: 0; width: 60; height: 60; color: \"#ffffff\"\n"
        + "              radius: 20; bottomRightRadius: 0 }\n"
        + "}";

    @Test
    void aPerCornerZeroOverridesTheUniformRadius() {
        Bitmap bmp = render(SQUARED_ONE, 60, 60);
        try {
            assertTrue(cornerIsRounded(bmp, 1, 1), "radius: 20 still rounds the unspecified corners");
            assertTrue(!cornerIsRounded(bmp, 58, 58),
                "bottomRightRadius: 0 must square that corner even though radius is 20 -- this is "
                + "the SegmentedButton case, where an end segment is round on one side only");
        } finally {
            bmp.close();
        }
    }

    private static final String UNIFORM =
        "import QtQuick\n"
        + "Item {\n"
        + "  width: 60; height: 60\n"
        + "  Rectangle { x: 0; y: 0; width: 60; height: 60; color: \"#ffffff\"; radius: 20 }\n"
        + "}";

    @Test
    void aUniformRadiusStillRoundsAllFourCorners() {
        Bitmap bmp = render(UNIFORM, 60, 60);
        try {
            assertTrue(cornerIsRounded(bmp, 1, 1), "top-left");
            assertTrue(cornerIsRounded(bmp, 58, 1), "top-right");
            assertTrue(cornerIsRounded(bmp, 1, 58), "bottom-left");
            assertTrue(cornerIsRounded(bmp, 58, 58), "bottom-right");
        } finally {
            bmp.close();
        }
    }

    private static final String SQUARE =
        "import QtQuick\n"
        + "Item {\n"
        + "  width: 60; height: 60\n"
        + "  Rectangle { x: 0; y: 0; width: 60; height: 60; color: \"#ffffff\" }\n"
        + "}";

    @Test
    void aRectangleWithNoRadiusKeepsEveryCorner() {
        Bitmap bmp = render(SQUARE, 60, 60);
        try {
            assertEquals(0xFFFFFFFF, bmp.getColor(1, 1), "no radius means the corner pixel is filled");
            assertEquals(0xFFFFFFFF, bmp.getColor(58, 58), "and so is the opposite one");
        } finally {
            bmp.close();
        }
    }

    /**
     * cornerRadius() must never hand out a negative radius.
     *
     * <p>Asserted on the value rather than on pixels, and that choice is the point: Skia reads a
     * negative radius as zero, so a pixel test passes whether or not the clamp exists -- verified
     * by removing the clamp, which left a pixel-based version of this test green. The contract
     * being pinned is the one the renderer relies on (radii reaching Skia are non-negative), not
     * an appearance that happens to survive without it.
     */
    @Test
    void cornerRadiusNeverReturnsNegative() {
        Rectangle r = new Rectangle();
        r.radius.set(-5);
        assertEquals(0f, r.cornerRadius(-1f), 0f,
            "an UNSET corner falls back to radius, which must be clamped rather than passed on");
        assertEquals(0f, r.cornerRadius(-3f), 0f, "an explicitly negative corner clamps too");
        r.radius.set(8);
        assertEquals(8f, r.cornerRadius(-1f), 0f, "an unset corner still inherits a sane radius");
        assertEquals(2f, r.cornerRadius(2f), 0f, "and an explicit corner wins over radius");
    }
}
