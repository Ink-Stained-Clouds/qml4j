package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A control can release focus it grabbed (ComboBox clears focus when its popup closes
// so the focus styling -- the purple underline -- returns to resting).
class ClearActiveFocusTest {
    private static Object call(Item root, String fn) {
        try { for (java.lang.reflect.Method m : root.getClass().getMethods())
                if (m.getName().equals(fn) && m.getParameterCount()==0) return m.invoke(root);
              throw new NoSuchMethodException(fn);
        } catch (Exception e){ throw new RuntimeException(e);} }

    @Test
    void grabThenReleaseFocus() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item { id: scene; width: 100; height: 100\n"
            + "  Item { id: field; width: 50; height: 20 }\n"
            + "  function grab() { field.forceActiveFocus() }\n"
            + "  function release() { field.clearActiveFocus() }\n"
            + "}");
        Item field = root.children.get(0);
        call(root, "grab");
        assertEquals(Boolean.TRUE, field.activeFocus.peek(), "focused after grab");
        call(root, "release");
        assertEquals(Boolean.FALSE, field.activeFocus.peek(), "unfocused after release");
    }
}
