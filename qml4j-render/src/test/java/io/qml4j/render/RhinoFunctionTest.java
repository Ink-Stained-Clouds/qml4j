package io.qml4j.render;

import io.qml4j.engine.Callable;
import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.js.RhinoFunction;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Phase 3: a QML function the ASM codegen cannot lower -- one using for-in -- runs on
// Rhino instead. It is registered as a __putFunction callable (RhinoFunction), so it
// is reachable by both bare and member calls, and its for-in body iterates a Java map.
class RhinoFunctionTest {

    private static void flush(QmlView v) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
    }

    @Test
    void forInFunctionRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  function sumValues(obj) {\n" +
            "    var s = 0;\n" +
            "    for (var k in obj) s = s + obj[k];\n" +
            "    return s;\n" +
            "  }\n" +
            "}");
        flush(v);

        Callable fn = root.__getFunction("sumValues");
        assertNotNull(fn, "for-in function should be registered as a callable");
        assertTrue(fn instanceof RhinoFunction,
            "sumValues should be a RhinoFunction, was " + fn.getClass().getName());

        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("a", 1L);
        obj.put("b", 2L);
        obj.put("c", 3L);
        assertEquals(6L, fn.call(new Object[]{obj}));   // for-in over a Java map
    }
}
