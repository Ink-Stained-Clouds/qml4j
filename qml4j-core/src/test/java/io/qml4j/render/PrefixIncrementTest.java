package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import io.qml4j.runtime.member.MemberAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Prefix increment/decrement (++i / --i) -- the MD3 Pro page uses `for (...; ++i)`.
// The grammar must parse it (Rhino executes the captured source).
class PrefixIncrementTest {

    @Test
    void prefixIncrementInForLoop() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n"
            + "Item {\n"
            + "  property int sum: 0\n"
            + "  property int down: 0\n"
            + "  Component.onCompleted: {\n"
            + "    var s = 0;\n"
            + "    for (var i = 0; i < 5; ++i) s = s + i;\n"
            + "    sum = s;\n"
            + "    var d = 10;\n"
            + "    --d;\n"
            + "    down = d;\n"
            + "  }\n"
            + "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertEquals(10, ((Number) MemberAccess.readMember(root, "sum")).intValue(), "0+1+2+3+4");
        assertEquals(9, ((Number) MemberAccess.readMember(root, "down")).intValue(), "--d");
    }
}
