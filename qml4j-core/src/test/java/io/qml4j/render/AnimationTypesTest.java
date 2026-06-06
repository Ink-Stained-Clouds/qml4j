package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.animation.ColorAnimation;
import io.qml4j.render.items.core.ColorMath;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.animation.NumberAnimation;
import io.qml4j.render.items.animation.OpacityAnimation;
import io.qml4j.render.items.animation.ParallelAnimation;
import io.qml4j.render.items.animation.PauseAnimation;
import io.qml4j.render.items.animation.PropertyAnimation;
import io.qml4j.render.items.core.Rectangle;
import io.qml4j.render.items.animation.RotationAnimation;
import io.qml4j.render.items.animation.ScriptAction;
import io.qml4j.render.items.animation.SequentialAnimation;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTypesTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static NumberAnimation numAnim(Object target, String prop, double from, double to, int durationMs) {
        NumberAnimation a = new NumberAnimation();
        a.target.set(target);
        a.property.set(prop);
        a.from.set(from);
        a.to.set(to);
        a.duration.set(durationMs);
        return a;
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
    void colorAnimationRejectsNumericPair() {
        ColorAnimation a = new ColorAnimation();
        assertFalse(a.acceptsTransition(16, 672));
        assertFalse(a.acceptsTransition(0.5, 1.0));
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
    void parallelAnimationRunsChildrenSimultaneously() {
        Rectangle r = new Rectangle();
        ParallelAnimation par = new ParallelAnimation();
        NumberAnimation a = numAnim(r, "x", 0, 100, 100);
        NumberAnimation b = numAnim(r, "y", 0, 200, 100);
        par.children.add(a); par.children.add(b);
        par.running.set(Boolean.TRUE);
        long t0 = 0L;
        par.tick(t0);
        par.tick(t0 + 50_000_000L);
        assertEquals(50.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        assertEquals(100.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        par.tick(t0 + 200_000_000L);
        assertEquals(100.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        assertEquals(200.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        assertEquals(Boolean.FALSE, par.running.peek());
    }

    @Test
    void sequentialAnimationRunsChildrenInOrder() {
        Rectangle r = new Rectangle();
        SequentialAnimation seq = new SequentialAnimation();
        NumberAnimation first = numAnim(r, "x", 0, 100, 100);
        NumberAnimation second = numAnim(r, "y", 0, 200, 100);
        seq.children.add(first); seq.children.add(second);

        seq.running.set(Boolean.TRUE);
        long t0 = 0L;
        seq.tick(t0);
        seq.tick(t0 + 50_000_000L);
        assertEquals(50.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        assertEquals(0.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        assertTrue(Boolean.TRUE.equals(first.running.peek()));
        assertFalse(Boolean.TRUE.equals(second.running.peek()));

        seq.tick(t0 + 100_000_000L);
        assertEquals(100.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        assertFalse(Boolean.TRUE.equals(first.running.peek()));
        assertTrue(Boolean.TRUE.equals(second.running.peek()));

        seq.tick(t0 + 150_000_000L);
        assertEquals(100.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        seq.tick(t0 + 250_000_000L);
        assertEquals(200.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        assertEquals(Boolean.FALSE, seq.running.peek());
    }

    @Test
    void nestedSequentialInParallel() {
        Rectangle r = new Rectangle();
        ParallelAnimation par = new ParallelAnimation();
        SequentialAnimation seq = new SequentialAnimation();
        seq.children.add(numAnim(r, "y", 0, 100, 100));
        seq.children.add(numAnim(r, "y", 100, 50, 100));
        par.children.add(numAnim(r, "x", 0, 200, 200));
        par.children.add(seq);
        par.running.set(Boolean.TRUE);
        long t0 = 0L;
        par.tick(t0);
        par.tick(t0 + 100_000_000L);
        assertEquals(100.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        assertEquals(100.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        par.tick(t0 + 200_000_000L);
        assertEquals(200.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        assertEquals(50.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        assertEquals(Boolean.FALSE, par.running.peek());
    }

    @Test
    void pauseAnimationStandaloneWaitsThenFinishes() {
        PauseAnimation p = new PauseAnimation();
        p.duration.set(100);
        p.running.set(Boolean.TRUE);
        long t0 = 0L;
        p.tick(t0);
        p.tick(t0 + 50_000_000L);
        assertEquals(Boolean.TRUE, p.running.peek());
        p.tick(t0 + 110_000_000L);
        assertEquals(Boolean.FALSE, p.running.peek());
    }

    @Test
    void sequentialAnimationHonorsPauseAnimation() {
        Rectangle r = new Rectangle();
        SequentialAnimation seq = new SequentialAnimation();
        seq.children.add(numAnim(r, "x", 0, 100, 100));
        PauseAnimation pause = new PauseAnimation();
        pause.duration.set(150);
        seq.children.add(pause);
        seq.children.add(numAnim(r, "x", 100, 200, 100));
        seq.running.set(Boolean.TRUE);
        long t0 = 0L;
        seq.tick(t0);
        seq.tick(t0 + 100_000_000L); // first done
        assertEquals(100.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        seq.tick(t0 + 200_000_000L); // mid-pause
        assertEquals(100.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        seq.tick(t0 + 260_000_000L); // pause done, second begins
        seq.tick(t0 + 360_000_000L); // second done
        assertEquals(200.0, ((Number) r.x.peek()).doubleValue(), 1e-6);
        assertEquals(Boolean.FALSE, seq.running.peek());
    }

    @Test
    void scriptActionFiresTriggerOnceWhenStarted() {
        ScriptAction sa = new ScriptAction();
        AtomicInteger hits = new AtomicInteger();
        sa.trigger.connect((Runnable) hits::incrementAndGet);
        sa.running.set(Boolean.TRUE);
        sa.tick(0L);
        assertEquals(1, hits.get());
        assertEquals(Boolean.FALSE, sa.running.peek());
        sa.tick(1_000_000L);
        assertEquals(1, hits.get());
    }

    @Test
    void sequentialAnimationFiresScriptActionAtItsTurn() {
        Rectangle r = new Rectangle();
        SequentialAnimation seq = new SequentialAnimation();
        seq.children.add(numAnim(r, "x", 0, 100, 100));
        ScriptAction sa = new ScriptAction();
        AtomicInteger hits = new AtomicInteger();
        sa.trigger.connect((Runnable) hits::incrementAndGet);
        seq.children.add(sa);
        seq.children.add(numAnim(r, "y", 0, 50, 100));
        seq.running.set(Boolean.TRUE);
        long t0 = 0L;
        seq.tick(t0);
        seq.tick(t0 + 50_000_000L);
        assertEquals(0, hits.get(), "script action must wait its turn");
        seq.tick(t0 + 120_000_000L);
        assertEquals(1, hits.get());
        seq.tick(t0 + 250_000_000L);
        assertEquals(50.0, ((Number) r.y.peek()).doubleValue(), 1e-6);
        assertEquals(Boolean.FALSE, seq.running.peek());
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
