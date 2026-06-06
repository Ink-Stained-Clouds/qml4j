package io.qml4j.render.items.window;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;
import io.qml4j.render.Painter;

public class Window extends Item {
    public final Property<String> color = new Property<>("#ffffff");
    public final Property<String> title = new Property<>("");

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        String c = color.peek();
        if (c == null || "transparent".equals(c)) return;
        p.fillRect(0, 0, w, h, p.alphaColor(c, alpha));
    }
}
