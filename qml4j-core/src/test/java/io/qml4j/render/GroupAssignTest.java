package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.core.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Whole-group assignment `font: other.font` binds each sub-property to the source
// group's same-named sub-property (reused by TextField's editor Text).
class GroupAssignTest {

    @Test
    void fontGroupAssignmentCopiesSubProperties() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  Text { id: a; font.pixelSize: 22; font.bold: true }\n"
            + "  Text { id: b; font: a.font }\n"
            + "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        Text b = (Text) root.children.get(1);
        assertEquals(22, b.font.pixelSize.peek().intValue(), "pixelSize copied from a.font");
        assertEquals(Boolean.TRUE, b.font.bold.peek(), "bold copied from a.font");
    }
}
