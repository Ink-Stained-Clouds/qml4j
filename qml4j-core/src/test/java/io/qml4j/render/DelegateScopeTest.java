package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A Repeater delegate can read a bare property of the component that declared the
// Repeater (not just its own index/modelData or scene ids). The delegate's parent
// is set before its bindings first evaluate, so the lookup walks the parent chain
// up to the enclosing component.
class DelegateScopeTest {

    @Test
    void delegateReadsOuterComponentProperty() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Item { property string tag: \"row-\"\n" +
            "  Repeater { model: 3\n" +
            "    Text { text: tag + index }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        StringBuilder seen = new StringBuilder();
        for (Item c : root.children) {
            if (c instanceof Text) seen.append(((Text) c).text.peek()).append(';');
        }
        assertEquals("row-0;row-1;row-2;", seen.toString());
    }
}
