package io.github.timer_err.qml4j.render.items.window;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.Painter;
import io.github.timer_err.qml4j.render.TextLayout;

public class Button extends AbstractButton {
    public final Property<String> color = new Property<>("#3b6fe0");
    public final Property<String> textColor = new Property<>("#ffffff");
    public final Property<String> downColor = new Property<>("#2c54aa");
    public final Property<Number> radius = new Property<>(6);
    public final Property<Number> fontSize = new Property<>(16);

    @Override
    public void measure(TextLayout t) {
        t.measureButton(this);
    }

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        boolean on = !Boolean.FALSE.equals(enabled.peek());
        boolean pressed = Boolean.TRUE.equals(down.peek()) || Boolean.TRUE.equals(checked.peek());
        float a = on ? alpha : alpha * 0.5f;
        float radius = Math.max(0f, this.radius.peekFloat());
        int bg = p.alphaColor(pressed ? downColor.peek() : color.peek(), a);
        p.fillRoundRect(0, 0, w, h, radius, bg);
        String label = text.peek();
        if (label == null || label.isEmpty()) return;
        float size = fontSize.peekFloat();
        p.drawCenteredText(label, w, h, p.alphaColor(textColor.peek(), a), size);
    }
}
