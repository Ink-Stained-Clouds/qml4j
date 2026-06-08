package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// A delegate handler resolves an enclosing-component scene id even after the delegate's
// subtree is reparented out of that component. The MD3 popup pattern reparents its
// overlay onto the scene root to render on top, which detaches the menu items from the
// Menu -- so the runtime parent-chain walk can no longer reach the Menu, but the id
// (`control`) still lexically names it. Scene ids must resolve via the captured root,
// not the live parent chain.
class DelegateIdResolveTest {

    private static long readInt(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).longValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Item find(Item node, String field) {
        try { node.getClass().getField(field); return node; }
        catch (NoSuchFieldException ignore) { }
        for (Item c : node.children) {
            Item r = find(c, field);
            if (r != null) return r;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void setBool(Item it, String prop, boolean val) {
        try {
            Field f = it.getClass().getField(prop);
            ((Property<Object>) f.get(it)).set(val);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void reparentedDelegateStillResolvesEnclosingId() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item { id: scene\n"
            + "  Item { id: control\n"                       // the "Menu"
            + "    property int closes: 0\n"
            + "    function close() { control.closes = control.closes + 1 }\n"
            + "    Item { id: overlayLayer\n"                // the reparenting overlay
            + "      Component { id: itemComponent\n"
            + "        Item {\n"
            + "          property var itemData\n"
            + "          property bool go: false\n"
            + "          onGoChanged: { if (control && control.close) control.close() }\n"
            + "        }\n"
            + "      }\n"
            + "      Repeater { model: 1\n"
            + "        delegate: Loader { required property var modelData\n"
            + "          sourceComponent: itemComponent }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); new Renderer().layoutOnly(root); } finally { dq.uninstall(); }

        // Reparent the overlay onto the scene root (MD3 popup technique): the delegate
        // items are no longer in the Menu (control) subtree.
        Item control = root.children.get(0);
        Item overlay = control.children.get(0);
        control.children.remove(overlay);
        root.children.add(overlay);
        overlay.parent.set(root);

        Item loaded = find(root, "go");
        assertNotNull(loaded, "loaded delegate item found");
        setBool(loaded, "go", true);
        assertEquals(1L, readInt(control, "closes"),
            "control.close() reached the enclosing id despite the reparent");
    }
}
