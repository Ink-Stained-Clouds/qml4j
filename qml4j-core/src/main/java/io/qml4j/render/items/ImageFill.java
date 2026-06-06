package io.qml4j.render.items;

public final class ImageFill {

    public enum Op { DRAW_RECT, TILE_X, TILE_Y, TILE_XY }

    public static final class Plan {
        public final Op op;
        public final float srcX, srcY, srcW, srcH;
        public final float dstX, dstY, dstW, dstH;
        public final float paintedWidth, paintedHeight;
        public final float tileStepX, tileStepY;
        public final float clipX, clipY, clipW, clipH;

        Plan(Op op,
             float sx, float sy, float sw, float sh,
             float dx, float dy, float dw, float dh,
             float pw, float ph,
             float tx, float ty,
             float cx, float cy, float cw, float ch) {
            this.op = op;
            this.srcX = sx; this.srcY = sy; this.srcW = sw; this.srcH = sh;
            this.dstX = dx; this.dstY = dy; this.dstW = dw; this.dstH = dh;
            this.paintedWidth = pw; this.paintedHeight = ph;
            this.tileStepX = tx; this.tileStepY = ty;
            this.clipX = cx; this.clipY = cy; this.clipW = cw; this.clipH = ch;
        }
    }

    public static Plan compute(String mode, float iw, float ih, float w, float h) {
        if (iw <= 0 || ih <= 0 || w <= 0 || h <= 0) return null;
        if (mode == null) mode = "Stretch";
        switch (mode) {
            case "PreserveAspectFit": return fit(iw, ih, w, h);
            case "PreserveAspectCrop": return crop(iw, ih, w, h);
            case "Tile": return tile(iw, ih, w, h, true, true);
            case "TileVertically": return tile(iw, ih, w, h, false, true);
            case "TileHorizontally": return tile(iw, ih, w, h, true, false);
            case "Pad": return pad(iw, ih, w, h);
            case "Stretch":
            default: return stretch(iw, ih, w, h);
        }
    }

    private static Plan stretch(float iw, float ih, float w, float h) {
        return new Plan(Op.DRAW_RECT,
            0, 0, iw, ih,
            0, 0, w, h,
            w, h,
            0, 0,
            0, 0, w, h);
    }

    private static Plan fit(float iw, float ih, float w, float h) {
        float scale = Math.min(w / iw, h / ih);
        float pw = iw * scale;
        float ph = ih * scale;
        float dx = (w - pw) / 2f;
        float dy = (h - ph) / 2f;
        return new Plan(Op.DRAW_RECT,
            0, 0, iw, ih,
            dx, dy, pw, ph,
            pw, ph,
            0, 0,
            0, 0, w, h);
    }

    private static Plan crop(float iw, float ih, float w, float h) {
        float scale = Math.max(w / iw, h / ih);
        float pw = iw * scale;
        float ph = ih * scale;
        float sw = w / scale;
        float sh = h / scale;
        float sx = (iw - sw) / 2f;
        float sy = (ih - sh) / 2f;
        return new Plan(Op.DRAW_RECT,
            sx, sy, sw, sh,
            0, 0, w, h,
            pw, ph,
            0, 0,
            0, 0, w, h);
    }

    private static Plan tile(float iw, float ih, float w, float h,
                             boolean repeatX, boolean repeatY) {
        Op op = repeatX && repeatY ? Op.TILE_XY : (repeatX ? Op.TILE_X : Op.TILE_Y);
        float dstW = repeatX ? iw : w;
        float dstH = repeatY ? ih : h;
        float stepX = repeatX ? iw : 0;
        float stepY = repeatY ? ih : 0;
        return new Plan(op,
            0, 0, iw, ih,
            0, 0, dstW, dstH,
            w, h,
            stepX, stepY,
            0, 0, w, h);
    }

    private static Plan pad(float iw, float ih, float w, float h) {
        return new Plan(Op.DRAW_RECT,
            0, 0, iw, ih,
            0, 0, iw, ih,
            iw, ih,
            0, 0,
            0, 0, w, h);
    }

    private ImageFill() {}
}
