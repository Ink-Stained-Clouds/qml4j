package io.qml4j.render;

import io.qml4j.render.items.ImageFill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageFillTest {

    private static final float EPS = 0.001f;

    @Test
    void stretchFillsDest() {
        ImageFill.Plan p = ImageFill.compute("Stretch", 100, 50, 400, 200);
        assertEquals(ImageFill.Op.DRAW_RECT, p.op);
        assertEquals(100f, p.srcW, EPS);
        assertEquals(50f, p.srcH, EPS);
        assertEquals(400f, p.dstW, EPS);
        assertEquals(200f, p.dstH, EPS);
        assertEquals(400f, p.paintedWidth, EPS);
        assertEquals(200f, p.paintedHeight, EPS);
    }

    @Test
    void stretchIsDefaultForNullMode() {
        ImageFill.Plan p = ImageFill.compute(null, 100, 50, 400, 200);
        assertEquals(ImageFill.Op.DRAW_RECT, p.op);
        assertEquals(400f, p.dstW, EPS);
    }

    @Test
    void preserveAspectFitLetterboxesWiderImage() {
        ImageFill.Plan p = ImageFill.compute("PreserveAspectFit", 200, 100, 400, 400);
        assertEquals(400f, p.dstW, EPS);
        assertEquals(200f, p.dstH, EPS);
        assertEquals(0f, p.dstX, EPS);
        assertEquals(100f, p.dstY, EPS);
        assertEquals(400f, p.paintedWidth, EPS);
        assertEquals(200f, p.paintedHeight, EPS);
    }

    @Test
    void preserveAspectFitPillarboxesTallerImage() {
        ImageFill.Plan p = ImageFill.compute("PreserveAspectFit", 100, 200, 400, 400);
        assertEquals(200f, p.dstW, EPS);
        assertEquals(400f, p.dstH, EPS);
        assertEquals(100f, p.dstX, EPS);
        assertEquals(0f, p.dstY, EPS);
    }

    @Test
    void preserveAspectCropTrimsHorizontally() {
        ImageFill.Plan p = ImageFill.compute("PreserveAspectCrop", 200, 100, 400, 400);
        assertEquals(400f, p.dstW, EPS);
        assertEquals(400f, p.dstH, EPS);
        assertEquals(100f, p.srcW, EPS);
        assertEquals(100f, p.srcH, EPS);
        assertEquals(50f, p.srcX, EPS);
        assertEquals(0f, p.srcY, EPS);
        assertEquals(800f, p.paintedWidth, EPS);
        assertEquals(400f, p.paintedHeight, EPS);
    }

    @Test
    void tileRepeatsBoth() {
        ImageFill.Plan p = ImageFill.compute("Tile", 50, 50, 200, 150);
        assertEquals(ImageFill.Op.TILE_XY, p.op);
        assertEquals(50f, p.tileStepX, EPS);
        assertEquals(50f, p.tileStepY, EPS);
        assertEquals(50f, p.dstW, EPS);
    }

    @Test
    void tileVerticallyStretchesXTilesY() {
        ImageFill.Plan p = ImageFill.compute("TileVertically", 50, 30, 200, 200);
        assertEquals(ImageFill.Op.TILE_Y, p.op);
        assertEquals(0f, p.tileStepX, EPS);
        assertEquals(30f, p.tileStepY, EPS);
        assertEquals(200f, p.dstW, EPS);
        assertEquals(30f, p.dstH, EPS);
    }

    @Test
    void tileHorizontallyTilesXStretchesY() {
        ImageFill.Plan p = ImageFill.compute("TileHorizontally", 30, 50, 200, 200);
        assertEquals(ImageFill.Op.TILE_X, p.op);
        assertEquals(30f, p.tileStepX, EPS);
        assertEquals(0f, p.tileStepY, EPS);
        assertEquals(30f, p.dstW, EPS);
        assertEquals(200f, p.dstH, EPS);
    }

    @Test
    void padDrawsAtNaturalSize() {
        ImageFill.Plan p = ImageFill.compute("Pad", 80, 60, 400, 400);
        assertEquals(80f, p.dstW, EPS);
        assertEquals(60f, p.dstH, EPS);
        assertEquals(80f, p.paintedWidth, EPS);
        assertEquals(60f, p.paintedHeight, EPS);
    }

    @Test
    void zeroDimensionsReturnNull() {
        assertNull(ImageFill.compute("Stretch", 0, 100, 200, 200));
        assertNull(ImageFill.compute("Stretch", 100, 100, 0, 200));
    }

    @Test
    void unknownModeFallsBackToStretch() {
        ImageFill.Plan p = ImageFill.compute("nonsense", 100, 100, 400, 200);
        assertNotNull(p);
        assertEquals(ImageFill.Op.DRAW_RECT, p.op);
        assertEquals(400f, p.dstW, EPS);
        assertEquals(200f, p.dstH, EPS);
    }
}
