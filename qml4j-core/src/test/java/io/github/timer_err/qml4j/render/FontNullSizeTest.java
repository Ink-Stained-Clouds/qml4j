package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.core.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A font.pixelSize bound to an undefined expression evaluates to null (QML-tolerant);
// effectiveFontSize must fall back to fontSize instead of NPEing in the measure pass.
class FontNullSizeTest {

    @Test
    void nullPixelSizeFallsBackToFontSize() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  property var missing\n"
            + "  Text { id: t; text: \"x\"; fontSize: 18; font.pixelSize: missing }\n"
            + "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        Text t = (Text) root.children.get(0);
        assertEquals(18f, t.effectiveFontSize(), 1e-6, "falls back to fontSize when pixelSize is null");
    }
}
