package io.qml4j.render.items.core;

import io.qml4j.engine.Signal;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.Context2D;
import io.qml4j.render.Painter;

// QtQuick Canvas: an imperative 2D drawing surface. The `onPaint` handler runs each
// frame with a 2D context (`getContext('2d')`) whose ops draw through Skija. Local
// coordinates -- the Renderer has already translated to this item's origin.
public class Canvas extends Item {

    public final Signal paint = new Signal(); // onPaint
    public final Property<Boolean> available = new Property<>(Boolean.TRUE);
    public final Property<String> contextType = new Property<>("2d");
    public final Property<String> renderStrategy = new Property<>("Immediate");
    public final Property<String> renderTarget = new Property<>("Image");

    // Set for the duration of a paint pass so the onPaint handler's getContext() returns
    // a context bound to the current frame's canvas.
    private Context2D ctx;

    public Object getContext(String type) {
        return ctx;
    }

    // The desktop/host render loop repaints every frame, so a repaint request just needs
    // the next frame -- no explicit invalidation queue yet.
    public void requestPaint() {
    }

    public void markDirty() {
    }

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        if (!available.peek()) return;
        ctx = p.context2D();
        try {
            paint.emit();
        } finally {
            ctx.dispose();
            ctx = null;
        }
    }
}
