package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.ColorAnimation;
import io.qml4j.render.items.ColorMath;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.OpacityAnimation;
import io.qml4j.render.items.PropertyAnimation;
import io.qml4j.render.items.Rectangle;
import io.qml4j.render.items.RotationAnimation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTypesTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void colorAnimationLerpsRedToGreenInHsv() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  color: \"#ff0000\"\n" +
            "  ColorAnimation {\n" +
            "    target: parent\n" +
            "    from: \"#ff0000\"; to: \"#00ff00\"; duration: 100\n" +
            "    running: true\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root;
        ColorAnimation anim = (ColorAnimation) r.children.get(0);
        anim.property.set("color");
        long t0 = 1_000_000_000L;
        anim.tick(t0);
        anim.tick(t0 + 50_000_000L); // 50%
        String mid = (String) r.color.peek();
        assertTrue(mid.startsWith("#"));
        int argb = ColorMath.parse(mid);
        // HSV midpoint between red (h=0) and green (h=120) is yellow (h=60) → high R+G, low B
        int rr = (argb >>> 16) & 0xFF;
        int gg = (argb >>> 8) & 0xFF;
        int bb = argb & 0xFF;
        assertTrue(rr > 200, "expected high R, got " + rr);
        assertTrue(gg > 200, "expected high G, got " + gg);
        assertTrue(bb < 40, "expected low B, got " + bb);
        anim.tick(t0 + 200_000_000L);
        assertEquals(0xFF00FF00, ColorMath.parse(r.color.peek()));
        assertEquals(Boolean.FALSE, anim.running.peek());
    }

    @Test
    void colorAnimationAcceptsStringPair() {
        ColorAnimation a = new ColorAnimation();
        assertTrue(a.acceptsTransition("#ff0000", "#00ff00"));
    }

    @Test
    void rotationAnimationShortestPicksNegativePath() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  rotation: 350\n" +
            "  RotationAnimation {\n" +
            "    target: parent\n" +
            "    from: 350; to: 10; duration: 100\n" +
            "    direction: \"Shortest\"\n" +
            "    running: true\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root;
        RotationAnimation anim = (RotationAnimation) r.children.get(0);
        anim.property.set("rotation");
        long t0 = 0L;
        anim.tick(t0);
        anim.tick(t0 + 50_000_000L);
        double mid = ((Number) r.rotation.peek()).doubleValue();
        assertEquals(360.0, mid, 0.5);
    }

    @Test
    void rotationAnimationClockwiseGoesForward() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  rotation: 350\n" +
            "  RotationAnimation {\n" +
            "    target: parent\n" +
            "    from: 350; to: 10; duration: 100\n" +
            "    direction: \"Clockwise\"\n" +
            "    running: true\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root;
        RotationAnimation anim = (RotationAnimation) r.children.get(0);
        anim.property.set("rotation");
        long t0 = 0L;
        anim.tick(t0);
        anim.tick(t0 + 50_000_000L);
        double mid = ((Number) r.rotation.peek()).doubleValue();
        assertEquals(360.0, mid, 0.5);
        anim.tick(t0 + 200_000_000L);
        assertEquals(370.0, ((Number) r.rotation.peek()).doubleValue(), 1e-6);
    }

    @Test
    void rotationAnimationCounterclockwiseGoesBackward() {
        RotationAnimation a = new RotationAnimation();
        Rectangle r = new Rectangle();
        a.target.set(r);
        a.property.set("rotation");
        a.from.set(10.0);
        a.to.set(350.0);
        a.duration.set(100);
        a.direction.set("Counterclockwise");
        a.running.set(Boolean.TRUE);
        long t0 = 0L;
        a.tick(t0);
        a.tick(t0 + 200_000_000L);
        // CCW: 350 wraps down to -10, end value = -10
        assertEquals(-10.0, ((Number) r.rotation.peek()).doubleValue(), 1e-6);
    }

    @Test
    void rotationAnimationNumericalUsesRawValues() {
        RotationAnimation a = new RotationAnimation();
        Rectangle r = new Rectangle();
        a.target.set(r);
        a.property.set("rotation");
        a.from.set(0.0);
        a.to.set(720.0);
        a.duration.set(100);
        a.direction.set("Numerical");
        a.running.set(Boolean.TRUE);
        long t0 = 0L;
        a.tick(t0);
        a.tick(t0 + 50_000_000L);
        assertEquals(360.0, ((Number) r.rotation.peek()).doubleValue(), 1e-6);
    }

    @Test
    void opacityAnimationAppliesOpacityWhenPropertyOmitted() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  opacity: 0\n" +
            "  OpacityAnimation {\n" +
            "    target: parent\n" +
            "    from: 0; to: 1; duration: 100\n" +
            "    running: true\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root;
        OpacityAnimation a = (OpacityAnimation) r.children.get(0);
        long t0 = 0L;
        a.tick(t0);
        a.tick(t0 + 50_000_000L);
        assertEquals(0.5, ((Number) r.opacity.peek()).doubleValue(), 1e-6);
    }

    @Test
    void propertyAnimationBaseRejectsNonNumericByDefault() {
        PropertyAnimation a = new PropertyAnimation();
        assertFalse(a.acceptsTransition("hello", "world"));
        assertTrue(a.acceptsTransition(1, 2));
    }

    @Test
    void colorMathFormatHexRoundTrips() {
        int argb = 0xFF8040C0;
        String s = ColorMath.formatHex(argb);
        assertEquals("#ff8040c0", s);
        assertEquals(argb, ColorMath.parse(s));
    }

    @Test
    void colorMathLerpHsvHalfwayBetweenBlackAndWhiteIsGray() {
        int mid = ColorMath.lerpHsv(0xFF000000, 0xFFFFFFFF, 0.5);
        int rr = (mid >>> 16) & 0xFF;
        int gg = (mid >>> 8) & 0xFF;
        int bb = mid & 0xFF;
        assertEquals(rr, gg);
        assertEquals(gg, bb);
        assertTrue(rr > 100 && rr < 160, "expected mid-gray, got " + rr);
    }

    @Test
    void colorMathLerpHsvPreservesAlpha() {
        int mid = ColorMath.lerpHsv(0x00000000, 0xFFFFFFFF, 0.5);
        int alpha = (mid >>> 24) & 0xFF;
        assertEquals(128, alpha, 1);
    }

    @Test
    void stateTransitionWithColorAnimationSpawnsEphemeralColorAnim() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  id: r\n" +
            "  width: 100; height: 100; color: \"#ff0000\"\n" +
            "  states: [State { name: \"go\"; PropertyChanges { target: r; color: \"#00ff00\" } }]\n" +
            "  transitions: [Transition { ColorAnimation { duration: 100 } }]\n" +
            "}");
        Rectangle r = (Rectangle) root;
        r.state.set("go");
        ColorAnimation eph = null;
        for (Item c : r.children) {
            if (c instanceof ColorAnimation && ((ColorAnimation) c).ephemeral) {
                eph = (ColorAnimation) c;
                break;
            }
        }
        assertNotNull(eph, "color transition must spawn an ephemeral ColorAnimation");
        assertEquals("color", eph.property.peek());
    }
}
