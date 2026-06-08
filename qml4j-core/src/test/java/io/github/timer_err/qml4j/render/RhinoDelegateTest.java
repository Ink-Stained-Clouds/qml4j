package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.engine.js.RhinoBinding;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Phase 5: a simple-expression binding inside a Repeater delegate runs on Rhino. The
// delegate scope resolves index / modelData / enclosing-scene names through
// DelegateScope.delegateLookup, the same walk the ASM backend uses.
class RhinoDelegateTest {

    private static Object bindingOf(Property<?> p) throws Exception {
        Field f = Property.class.getDeclaredField("binding");
        f.setAccessible(true);
        return f.get(p);
    }

    @Test
    void delegateBindingRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property var items: [{ v: 10 }, { v: 20 }, { v: 30 }]\n" +
            "  property int bump: 1\n" +
            "  Repeater {\n" +
            "    model: root.items\n" +
            "    Text { width: modelData.v * 2 + index + root.bump }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        List<Text> rows = new ArrayList<>();
        for (Item c : root.children) if (c instanceof Text) rows.add((Text) c);
        assertEquals(3, rows.size());

        assertTrue(bindingOf(rows.get(0).width) instanceof RhinoBinding,
            "delegate binding should be a RhinoBinding");
        assertEquals(21, rows.get(0).width.peek().intValue());   // 10*2 + 0 + 1
        assertEquals(42, rows.get(1).width.peek().intValue());   // 20*2 + 1 + 1
        assertEquals(63, rows.get(2).width.peek().intValue());   // 30*2 + 2 + 1
    }

    // Phase 5b: a function-style (IIFE) binding and a grouped binding inside a delegate
    // also run on Rhino, resolving modelData/index through delegateLookup.
    @Test
    void delegateIifeAndGroupedBindingsRunOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property var items: [{ v: 10 }, { v: 20 }]\n" +
            "  Repeater {\n" +
            "    model: root.items\n" +
            "    Rectangle {\n" +
            "      width: { var base = modelData.v; if (index === 0) return base; return base + 100 }\n" +
            "      border.width: index === 0 ? 3 : 1\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        List<io.github.timer_err.qml4j.render.items.core.Rectangle> rects = new ArrayList<>();
        for (Item c : root.children) {
            if (c instanceof io.github.timer_err.qml4j.render.items.core.Rectangle) rects.add((io.github.timer_err.qml4j.render.items.core.Rectangle) c);
        }
        assertEquals(2, rects.size());

        assertTrue(bindingOf(rects.get(0).width) instanceof RhinoBinding, "delegate IIFE binding");
        assertEquals(10, rects.get(0).width.peek().intValue());    // base, index 0
        assertEquals(120, rects.get(1).width.peek().intValue());   // base + 100, index 1

        assertTrue(bindingOf(rects.get(0).border.width) instanceof RhinoBinding, "delegate grouped binding");
        assertEquals(3, rects.get(0).border.width.peek().intValue());
        assertEquals(1, rects.get(1).border.width.peek().intValue());
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<io.github.timer_err.qml4j.engine.SignalHandler> handlersOf(io.github.timer_err.qml4j.engine.Signal s) throws Exception {
        Field f = io.github.timer_err.qml4j.engine.Signal.class.getDeclaredField("handlers");
        f.setAccessible(true);
        return (java.util.List<io.github.timer_err.qml4j.engine.SignalHandler>) f.get(s);
    }

    // Phase 5c: a signal handler inside a delegate runs on Rhino, resolving modelData /
    // index / enclosing-id member access through delegateLookup.
    @Test
    void delegateHandlerRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property int total: 0\n" +
            "  property var items: [{ v: 5 }, { v: 7 }]\n" +
            "  Repeater {\n" +
            "    model: root.items\n" +
            "    Rectangle {\n" +
            "      signal tapped()\n" +
            "      onTapped: root.total = root.total + modelData.v + index\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        List<io.github.timer_err.qml4j.render.items.core.Rectangle> rects = new ArrayList<>();
        for (Item c : root.children) {
            if (c instanceof io.github.timer_err.qml4j.render.items.core.Rectangle) rects.add((io.github.timer_err.qml4j.render.items.core.Rectangle) c);
        }
        assertEquals(2, rects.size());

        for (io.github.timer_err.qml4j.render.items.core.Rectangle r : rects) {
            io.github.timer_err.qml4j.engine.Signal tapped = (io.github.timer_err.qml4j.engine.Signal) r.getClass().getField("tapped").get(r);
            assertTrue(handlersOf(tapped).get(0) instanceof io.github.timer_err.qml4j.engine.js.RhinoHandler,
                "delegate handler should be a RhinoHandler");
            tapped.emit();
        }
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        io.github.timer_err.qml4j.engine.binding.Property<?> total =
            (io.github.timer_err.qml4j.engine.binding.Property<?>) root.getClass().getField("total").get(root);
        assertEquals(13, ((Number) total.peek()).intValue());   // (5+0) + (7+1)
    }

    // Regression (NavigationBar crash): a delegate handler ON a compound child whose
    // own internal `id: root` leaks as a field must still resolve a bare `root` to the
    // ENCLOSING component (here the test root), not the child -- otherwise
    // `root.picked = index` writes to the child and throws "no member 'picked'".
    @Test
    void delegateHandlerResolvesEnclosingIdNotChildLeakedId() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put("probe/qmldir", "Inner 1.0 Inner.qml".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        files.put("probe/Inner.qml",
            ("import QtQuick\nItem { id: root; signal poke() }\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import probe\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property int picked: -1\n" +
            "  Repeater {\n" +
            "    model: 2\n" +
            "    Item {\n" +
            "      id: navItem\n" +
            "      Inner { onPoke: root.picked = index }\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        // navItem delegates -> their Inner child -> emit poke (index 1 last).
        for (Item navItem : root.children) {
            for (Item inner : navItem.children) {
                Field pokeField;
                try { pokeField = inner.getClass().getField("poke"); }
                catch (NoSuchFieldException e) { continue; }
                io.github.timer_err.qml4j.engine.Signal poke = (io.github.timer_err.qml4j.engine.Signal) pokeField.get(inner);
                assertTrue(handlersOf(poke).get(0) instanceof io.github.timer_err.qml4j.engine.js.RhinoHandler);
                poke.emit();
            }
        }
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        io.github.timer_err.qml4j.engine.binding.Property<?> picked =
            (io.github.timer_err.qml4j.engine.binding.Property<?>) root.getClass().getField("picked").get(root);
        assertEquals(1, ((Number) picked.peek()).intValue());   // resolved enclosing root, not Inner
    }

    // Phase 6: a delegate handler with a BARE call to an enclosing root function, and a
    // call to a delegate-LOCAL function, both run on Rhino (delegateCallableOwner walks
    // the parent chain for callables).
    @Test
    void delegateBareCallAndLocalFunctionRunOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property int sum: 0\n" +
            "  function add(x) { root.sum = root.sum + x }\n" +   // enclosing root function
            "  Repeater {\n" +
            "    model: 2\n" +
            "    Item {\n" +
            "      function contribute(i) { return i * 10 + 1 }\n" + // delegate-local function
            "      signal go()\n" +
            "      onGo: add(contribute(index))\n" +                // bare calls: enclosing + local
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        for (Item item : root.children) {
            Field goField;
            try { goField = item.getClass().getField("go"); }
            catch (NoSuchFieldException e) { continue; }
            io.github.timer_err.qml4j.engine.Signal go = (io.github.timer_err.qml4j.engine.Signal) goField.get(item);
            assertTrue(handlersOf(go).get(0) instanceof io.github.timer_err.qml4j.engine.js.RhinoHandler);
            go.emit();
        }
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        io.github.timer_err.qml4j.engine.binding.Property<?> sum =
            (io.github.timer_err.qml4j.engine.binding.Property<?>) root.getClass().getField("sum").get(root);
        assertEquals(12, ((Number) sum.peek()).intValue());   // (0*10+1) + (1*10+1) = 1 + 11
    }

    // A delegate's bare call to a root function must keep resolving after the delegate
    // subtree is reparented away from the component (a Menu/DatePicker pops its overlay
    // onto the scene root). The parent walk then misses the root, so resolution falls back
    // to the captured component root -- like a scene id.
    @Test
    void delegateBareCallResolvesViaCapturedRootAfterReparent() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Item { id: root\n" +
            "  function dbl(x) { return x * 2 }\n" +
            "  property int sel: 1\n" +
            "  Item { id: holder\n" +
            "    Repeater { id: rep; model: 2\n" +
            "      Rectangle { property int vv: dbl(index) + root.sel }\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        io.github.timer_err.qml4j.render.items.view.Repeater rep =
            (io.github.timer_err.qml4j.render.items.view.Repeater) root.getClass().getField("rep").get(root);
        Item holder = (Item) root.getClass().getField("holder").get(root);
        // Detach the delegate subtree from the component (overlay reparented to scene root).
        Item detached = new io.github.timer_err.qml4j.render.items.core.Item();
        root.children.remove(holder);
        detached.children.add(holder);
        holder.parent.set(detached);

        // Change the root property the delegate binding depends on -> re-evaluate.
        io.github.timer_err.qml4j.engine.binding.Property<Object> sel =
            (io.github.timer_err.qml4j.engine.binding.Property<Object>) (io.github.timer_err.qml4j.engine.binding.Property<?>)
                root.getClass().getField("sel").get(root);
        dq.install();
        try { sel.set(5L); dq.flush(); } finally { dq.uninstall(); }

        Item d1 = rep.instances().get(1);
        io.github.timer_err.qml4j.engine.binding.Property<?> vv =
            (io.github.timer_err.qml4j.engine.binding.Property<?>) d1.getClass().getField("vv").get(d1);
        assertEquals(7, ((Number) vv.peek()).intValue());  // dbl(1)=2 + sel 5 = 7
    }
}
