package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Canvas;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// The Canvas item + its onPaint/getContext('2d') bridge compile and instantiate:
// `getContext` resolves as a Canvas method (bare call in the handler) and the 2D
// context ops are member calls. Actual drawing needs native Skija (covered by the
// running app), but the compile/instantiate path is locked here.
class CanvasLoadTest {

    private static Item load(String body) {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load("import QtQuick\nItem { width: 50; height: 50\n" + body + "\n}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        return root;
    }

    @Test
    void canvasWithOnPaintCompiles() {
        Item root = load(
            "Canvas { width: 50; height: 50\n"
            + "  onPaint: {\n"
            + "    var ctx = getContext('2d')\n"
            + "    ctx.fillStyle = '#ff0000'\n"
            + "    ctx.beginPath()\n"
            + "    ctx.arc(25, 25, 20, 0, 2 * Math.PI)\n"
            + "    ctx.fill()\n"
            + "    ctx.strokeStyle = '#00ff00'\n"
            + "    ctx.lineWidth = 2\n"
            + "    ctx.stroke()\n"
            + "  }\n"
            + "}");
        assertFalse(root.children.isEmpty());
        assertNotNull(root.children.get(0));
        org.junit.jupiter.api.Assertions.assertTrue(root.children.get(0) instanceof Canvas);
    }
}
