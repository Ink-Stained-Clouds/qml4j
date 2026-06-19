package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

// Repeater windowing: with windowCount set, only `windowCount` delegates exist and
// each carries its GLOBAL index, so a list of any length costs a fixed number of
// rows. Sliding windowStart reuses the delegates in place (no rebuild).
class RepeaterWindowTest {

    private Item scene;
    private Item rep;
    private DirtyQueue dq;

    private static Object peek(Object o, String field) {
        try {
            Field f = o.getClass().getField(field);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static int idx(Item delegate) { return ((Number) peek(delegate, "index")).intValue(); }
    private static long y(Item delegate) { return ((Number) peek(delegate, "y")).longValue(); }

    @SuppressWarnings("unchecked")
    private static void setProp(Item root, String name, Object value) {
        try {
            Field f = root.getClass().getField(name);
            ((Property<Object>) f.get(root)).set(value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Item delegateAt(int i) {
        try { return (Item) rep.getClass().getMethod("itemAt", int.class).invoke(rep, i); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private int count() {
        try { return (Integer) rep.getClass().getMethod("count").invoke(rep); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private void settle() {
        dq.install();
        try { dq.flush(); new Renderer().layoutOnly(scene); } finally { dq.uninstall(); }
    }

    private static Item findRepeater(Item node) {
        if (node.getClass().getSimpleName().equals("Repeater")) return node;
        for (Item c : node.children) {
            Item r = findRepeater(c);
            if (r != null) return r;
        }
        return null;
    }

    private void load() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        scene = v.load(
            "import QtQuick\n"
            + "Item { id: scene; width: 100; height: 100\n"
            + "  property var data: [\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\"]\n"  // 8 rows
            + "  property int start: 0\n"
            + "  property var win: 3\n"
            + "  Item { id: holder; anchors.fill: parent\n"
            + "    Repeater { id: rep; model: scene.data\n"
            + "      windowStart: scene.start; windowCount: scene.win\n"
            + "      delegate: Rectangle { width: 100; height: 10; y: index * 10 } }\n"
            + "  }\n"
            + "}");
        rep = findRepeater(scene);
        dq = v.dirtyQueue();
        settle();
    }

    @Test
    void windowsToCountWithGlobalIndices() {
        load();
        assertEquals(3, count(), "only windowCount delegates exist");
        assertEquals(0, idx(delegateAt(0)));
        assertEquals(2, idx(delegateAt(2)), "delegate index is global");
        assertEquals("a", peek(delegateAt(0), "modelData"));
        assertEquals("c", peek(delegateAt(2), "modelData"));
        assertEquals(0L, y(delegateAt(0)));
        assertEquals(20L, y(delegateAt(2)), "y = index*10 places it at the global offset");
    }

    @Test
    void slideReusesDelegatesInPlace() {
        load();
        // window [0,1,2]; remember each delegate object by its global index.
        java.util.Map<Integer, Item> before = new java.util.HashMap<>();
        for (int i = 0; i < 3; i++) before.put(idx(delegateAt(i)), delegateAt(i));
        java.util.Set<Item> beforeObjs = new java.util.HashSet<>(before.values());
        Item rowAt2 = before.get(2);   // stays in window after sliding to [2,3,4]

        setProp(scene, "start", 2L);
        settle();

        assertEquals(3, count(), "count unchanged across a slide");
        // No new delegate objects are created: the same three are reused.
        java.util.Set<Item> afterObjs = new java.util.HashSet<>();
        java.util.Map<Integer, Object> afterData = new java.util.HashMap<>();
        for (int i = 0; i < 3; i++) {
            afterObjs.add(delegateAt(i));
            afterData.put(idx(delegateAt(i)), peek(delegateAt(i), "modelData"));
        }
        assertEquals(beforeObjs, afterObjs, "slide recycles the existing delegates (no rebuild)");
        // The window is now exactly [2,3,4] with matching content.
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(2, 3, 4)),
                afterData.keySet(), "global indices follow the window");
        assertEquals("c", afterData.get(2));
        assertEquals("e", afterData.get(4));
        // Minimal churn: the delegate that was showing index 2 still shows index 2 --
        // it is the same object, untouched, while only the two strays were repointed.
        Item stillAt2 = null;
        for (int i = 0; i < 3; i++) if (idx(delegateAt(i)) == 2) stillAt2 = delegateAt(i);
        assertSame(rowAt2, stillAt2, "the in-window row is the same untouched delegate");
        assertEquals("c", peek(stillAt2, "modelData"));
        assertEquals(20L, y(stillAt2));
    }

    @Test
    void clampsWindowAtTail() {
        load();
        setProp(scene, "start", 6L);   // maxStart = 8-3 = 5
        settle();
        assertEquals(3, count());
        assertEquals(5, idx(delegateAt(0)), "start clamped to count-window");
        assertEquals(7, idx(delegateAt(2)), "window stays full and inside the list");
        assertEquals("h", peek(delegateAt(2), "modelData"));
    }

    @Test
    void nullWindowBuildsFullList() {
        load();
        setProp(scene, "win", null);   // no windowing -> every row
        settle();
        assertEquals(8, count(), "windowCount null builds the whole model");
        assertEquals(0, idx(delegateAt(0)));
        assertEquals(7, idx(delegateAt(7)));
    }

    @Test
    void growingWindowRebuilds() {
        load();
        setProp(scene, "win", 5L);
        settle();
        assertEquals(5, count(), "a changed window size rebuilds to the new count");
        assertEquals(0, idx(delegateAt(0)));
        assertEquals(4, idx(delegateAt(4)));
    }
}
