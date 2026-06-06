package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.view.Repeater;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A binding inside a Repeater delegate may reference a delegate-local id.
class DelegateIdTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static Object prop(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void delegateBindingResolvesLocalId() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "Column {\n" +
            "  Repeater {\n" +
            "    id: rep\n" +
            "    model: 2\n" +
            "    Rectangle {\n" +
            "      property int echo: inner.width + index\n" +
            "      Rectangle { id: inner; width: 40 }\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        Repeater rep = (Repeater) reflectField(root, "rep");
        assertEquals(2, rep.instances().size());
        // echo = inner.width(40) + index
        assertEquals(40L, ((Number) prop(rep.instances().get(0), "echo")).longValue());
        assertEquals(41L, ((Number) prop(rep.instances().get(1), "echo")).longValue());
    }

    private static Object reflectField(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return f.get(o);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
