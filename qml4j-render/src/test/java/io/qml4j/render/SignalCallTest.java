package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Invoking a signal as a function — control.clicked() — emits it.
class SignalCallTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void callingSignalAsFunctionEmits() {
        QmlView v = newView();
        Item root = v.load(
            "import QtQuick\n" +
            "Item {\n" +
            "  signal poked()\n" +
            "  property int hits: 0\n" +
            "  onPoked: hits = hits + 1\n" +
            "  function fire() { poked() }\n" +
            "}");
        try {
            for (Method m : root.getClass().getMethods()) {
                if (m.getName().equals("fire") && m.getParameterCount() == 0) { m.invoke(root); break; }
            }
            Field f = root.getClass().getField("hits");
            assertEquals(1L, ((Property<?>) f.get(root)).peek());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
