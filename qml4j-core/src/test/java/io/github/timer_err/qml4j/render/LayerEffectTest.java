package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.effect.ColorOverlay;
import io.github.timer_err.qml4j.render.items.effect.DropShadow;
import io.github.timer_err.qml4j.render.items.effect.Glow;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerEffectTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void layerEnabledParses() {
        Item root = newView().load(
            "Rectangle { width: 50; height: 50; layer.enabled: true }");
        assertTrue(root.layer.enabled.peek());
    }

    @Test
    void dropShadowEffectGraph() {
        Item root = newView().load(
            "Rectangle {\n" +
            "  width: 50; height: 50\n" +
            "  layer.enabled: true\n" +
            "  layer.effect: DropShadow { offsetX: 4; offsetY: 6; radius: 8; color: \"#80000000\" }\n" +
            "}");
        Object effect = root.layer.effect.peek();
        assertInstanceOf(DropShadow.class, effect);
        DropShadow d = (DropShadow) effect;
        assertEquals(4L, d.offsetX.peek().longValue());
        assertEquals(6L, d.offsetY.peek().longValue());
        assertEquals(8L, d.radius.peek().longValue());
        assertEquals("#80000000", d.color.peek());
    }

    @Test
    void glowEffectGraph() {
        Item root = newView().load(
            "Rectangle {\n" +
            "  width: 50; height: 50\n" +
            "  layer.enabled: true\n" +
            "  layer.effect: Glow { radius: 12; color: \"#33ddaa\" }\n" +
            "}");
        Object effect = root.layer.effect.peek();
        assertInstanceOf(Glow.class, effect);
        Glow g = (Glow) effect;
        assertEquals(12L, g.radius.peek().longValue());
        assertEquals("#33ddaa", g.color.peek());
    }

    @Test
    void colorOverlayEffectGraph() {
        Item root = newView().load(
            "Rectangle {\n" +
            "  width: 50; height: 50\n" +
            "  layer.enabled: true\n" +
            "  layer.effect: ColorOverlay { color: \"#ff0000\" }\n" +
            "}");
        Object effect = root.layer.effect.peek();
        assertInstanceOf(ColorOverlay.class, effect);
        assertEquals("#ff0000", ((ColorOverlay) effect).color.peek());
    }

    @Test
    void layerDefaultsDisabledNoEffect() {
        Item root = newView().load("Rectangle { width: 50; height: 50 }");
        assertEquals(Boolean.FALSE, root.layer.enabled.peek());
        assertEquals(null, root.layer.effect.peek());
    }

    @Test
    void effectOnNestedChild() {
        Item root = newView().load(
            "Item {\n" +
            "  width: 100; height: 100\n" +
            "  Rectangle {\n" +
            "    width: 40; height: 40\n" +
            "    layer.enabled: true\n" +
            "    layer.effect: Glow { radius: 5; color: \"#ffffff\" }\n" +
            "  }\n" +
            "}");
        Item child = root.children.get(0);
        assertTrue(child.layer.enabled.peek());
        assertInstanceOf(Glow.class, child.layer.effect.peek());
    }
}
