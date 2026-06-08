package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Reproduces the MD3 NavigationRail "menu" toggle: the rail's `header` is a
// Component declared in the OUTER page scope but instantiated by the rail's own
// internal Loader. The menu button's `onClicked: isRail = !isRail` writes a FREE
// identifier that must resolve, across the Loader boundary, to the outer page's
// property -- and the binding `extended: !isRail` must then react.
class LoaderComponentScopeWriteTest {

    private static final String SRC =
        "import QtQuick\n"
        + "Item {\n"
        + "  id: page\n"
        + "  width: 400; height: 400\n"
        + "  property bool isRail: true\n"
        + "  property int clicks: 0\n"
        + "  property Component hdr: Component {\n"
        + "    MouseArea { width: 100; height: 100\n"
        + "      onClicked: { clicks = clicks + 1; isRail = !isRail }\n"
        + "    }\n"
        + "  }\n"
        + "  Item {\n"
        + "    id: rail\n"
        + "    width: 200; height: 400\n"
        + "    property bool extended: !page.isRail\n"
        + "    property real railWidth: extended ? 240 : 80\n"
        + "    Loader { width: 100; height: 100; sourceComponent: page.hdr }\n"
        + "  }\n"
        + "}";

    private static long asLong(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Number) ((Property<?>) f.get(o)).peek()).longValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static boolean asBool(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return Boolean.TRUE.equals(((Property<?>) f.get(o)).peek());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void layout(QmlView v, Item root) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { new Renderer().layoutOnly(root); } finally { dq.uninstall(); }
    }

    @Test
    void menuClickFlipsOuterScopePropertyAndPropagates() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item page = v.load(SRC);
        page.width.set(400.0);
        page.height.set(400.0);
        layout(v, page);

        Item rail = page.children.get(0);
        assertEquals(true, asBool(page, "isRail"), "starts as rail");
        assertEquals(false, asBool(rail, "extended"), "rail not extended initially");
        assertEquals(80L, (long) ((Number) get(rail, "railWidth")).doubleValue(), "narrow initially");

        // Click the menu button (loaded by the rail's internal Loader at 0,0).
        v.dispatchClick(20, 20);
        layout(v, page);

        assertEquals(1L, asLong(page, "clicks"), "menu onClicked fired");
        assertEquals(false, asBool(page, "isRail"), "isRail flipped via free-identifier write");
        assertEquals(true, asBool(rail, "extended"), "extended binding reacted");
        assertEquals(240L, (long) ((Number) get(rail, "railWidth")).doubleValue(), "rail widened");
    }

    private static Object get(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
