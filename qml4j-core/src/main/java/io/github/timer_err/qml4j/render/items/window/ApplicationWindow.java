package io.github.timer_err.qml4j.render.items.window;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;

public class ApplicationWindow extends Window {
    public final Property<Item> header = new Property<>(null);
    public final Property<Item> footer = new Property<>(null);
    public final Property<Item> menuBar = new Property<>(null);

    public void layoutChrome(float w, float h) {
        Item m = menuBar.peek();
        float top = 0f;
        if (m != null) {
            m.x.set(0);
            m.y.set(top);
            m.width.set(w);
            top += m.height.peekFloat();
        }
        Item hdr = header.peek();
        if (hdr != null) {
            hdr.x.set(0);
            hdr.y.set(top);
            hdr.width.set(w);
        }
        Item ftr = footer.peek();
        if (ftr != null) {
            ftr.x.set(0);
            ftr.y.set(h - ftr.height.peekFloat());
            ftr.width.set(w);
        }
    }

    public float contentTop() {
        float top = 0f;
        Item m = menuBar.peek();
        if (m != null) top += m.height.peekFloat();
        Item hdr = header.peek();
        if (hdr != null) top += hdr.height.peekFloat();
        return top;
    }

    public float contentBottom(float h) {
        Item ftr = footer.peek();
        return ftr != null ? h - ftr.height.peekFloat() : h;
    }
}
