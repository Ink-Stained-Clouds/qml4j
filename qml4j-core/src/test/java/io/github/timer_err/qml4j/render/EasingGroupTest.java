package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.animation.NumberAnimation;
import io.github.timer_err.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// easing is a grouped value-type property (`easing.type: Easing.OutQuad`),
// matching Qt QML. The flat `easing: "easeOutQuad"` string form is gone.
class EasingGroupTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void easingTypeGroupDrivesCurve() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle { id: r\n" +
            "  width: 0; height: 10\n" +
            "  NumberAnimation {\n" +
            "    target: r\n" +
            "    from: 0; to: 100; duration: 100\n" +
            "    easing.type: Easing.OutQuad\n" +
            "    running: true\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root;
        NumberAnimation anim = (NumberAnimation) r.children.get(0);
        anim.property.set("width");
        anim.tick(0L);
        anim.tick(50_000_000L); // 50%, OutQuad eased = 1 - 0.25 = 0.75
        assertEquals(75.0, r.width.peek().doubleValue(), 1e-6);
    }

    @Test
    void easingTypeDefaultsToLinear() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle { id: r\n" +
            "  width: 0; height: 10\n" +
            "  NumberAnimation {\n" +
            "    target: r\n" +
            "    from: 0; to: 100; duration: 100\n" +
            "    running: true\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root;
        NumberAnimation anim = (NumberAnimation) r.children.get(0);
        anim.property.set("width");
        anim.tick(0L);
        anim.tick(50_000_000L); // 50%, linear = 0.5
        assertEquals(50.0, r.width.peek().doubleValue(), 1e-6);
    }
}
