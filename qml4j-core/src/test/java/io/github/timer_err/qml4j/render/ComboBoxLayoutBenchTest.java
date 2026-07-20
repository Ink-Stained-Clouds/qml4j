package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Diagnostic + guard for the reported "ComboBox animation halves the framerate". Loads the
// real MD3 ComboBox inside a large tree, drives its open animation, and measures how many
// nodes the incremental layout re-measures per animation frame. If that count stays tiny
// relative to the whole tree, the animation cost is in PAINT, not layout (this refactor's
// scope); a whole-tree count would mean an invalidation leak to fix.
class ComboBoxLayoutBenchTest {

    // Serve any md3/Core/* file (and its qmldir, which imports the whole module) from the
    // shared-qml test resources on the classpath.
    private static byte[] classpath(String path) {
        try (InputStream in = ComboBoxLayoutBenchTest.class.getResourceAsStream("/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private static int countNodes(Item n) {
        int c = 1;
        for (int i = 0; i < n.children.size(); i++) c += countNodes(n.children.get(i));
        return c;
    }

    private static void set(Item o, String field, Object v) {
        try {
            Field f = o.getClass().getField(field);
            @SuppressWarnings("unchecked")
            Property<Object> p = (Property<Object>) f.get(o);
            p.set(v);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void openAnimationStaysLocal() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(ComboBoxLayoutBenchTest::classpath);

        // A big surrounding tree so "whole-tree relayout" would show up as a large node count.
        StringBuilder qml = new StringBuilder(
            "import QtQuick\nimport md3.Core\n"
            + "Item { width: 400; height: 800\n"
            + "  Column { id: col; width: 360\n");
        for (int i = 0; i < 30; i++) {
            qml.append("    Rectangle { width: 360; height: 20 }\n");
        }
        qml.append(
            "    ComboBox { id: cb; width: 240; label: \"Pick\";\n"
            + "      model: [\"Alpha\",\"Beta\",\"Gamma\",\"Delta\",\"Epsilon\"] }\n"
            + "  }\n"
            + "}");

        Item root = v.load(qml.toString());
        Item cb = findComboBox(root);
        assertNotNull(cb, "ComboBox loaded");

        v.pumpLayout(); // first full layout
        int total = countNodes(root);
        int fullMeasured = v.renderer().measuredNodeCount();
        System.out.println("[combo-bench] total nodes=" + total + " full-layout measured=" + fullMeasured);

        // Trigger the open animation (label float + arrow rotation Behaviors).
        set(cb, "menuOpen", Boolean.TRUE);

        int maxPerFrame = 0;
        long t = 0;
        StringBuilder trace = new StringBuilder();
        for (int frame = 0; frame < 16; frame++) {
            t += 16_000_000L; // 16 ms
            v.tickAnimations(t);
            v.pumpLayout();
            int m = v.renderer().measuredNodeCount();
            maxPerFrame = Math.max(maxPerFrame, m);
            trace.append(m).append(frame == 15 ? "" : ",");
        }
        System.out.println("[combo-bench] per-frame measured during open anim: [" + trace + "] max=" + maxPerFrame);

        // The animation must not re-measure anything close to the whole tree. Generous bound:
        // well under the total, proving the incremental path keeps the animation local.
        assertTrue(maxPerFrame < total / 2,
            "open animation stayed local: max " + maxPerFrame + " of " + total + " nodes");
    }

    private static Item findComboBox(Item n) {
        if (hasField(n, "menuOpen")) return n;
        for (int i = 0; i < n.children.size(); i++) {
            Item r = findComboBox(n.children.get(i));
            if (r != null) return r;
        }
        return null;
    }

    private static boolean hasField(Item n, String field) {
        try { n.getClass().getField(field); return true; }
        catch (NoSuchFieldException e) { return false; }
    }
}
