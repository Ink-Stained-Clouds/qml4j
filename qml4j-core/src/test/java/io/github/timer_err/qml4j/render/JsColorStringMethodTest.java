package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.runtime.invoke.MethodInvocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A String that happens to look like a hex colour ("#ff33aa55") is wrapped as JsColor when it
// crosses into JS. JsColor must still behave as a string (charAt/substring/length/...), not just
// expose r/g/b/a -- otherwise a normal string op on it used to silently abort the function.
class JsColorStringMethodTest {

    private static void flush(QmlView v) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
    }

    @SuppressWarnings("unchecked")
    private static void set(Item o, String n, Object val) throws Exception {
        ((Property<Object>) o.getClass().getField(n).get(o)).set(val);
    }

    private static Object peek(Item o, String n) throws Exception {
        return ((Property<?>) o.getClass().getField(n).get(o)).peek();
    }

    static final String QML =
        "import QtQuick\n" +
        "Item { id: cp; width: 10; height: 10\n" +
        "  property real h: -1\n" +
        "  property string first: \"\"\n" +
        "  property bool done: false\n" +
        "  function fRaw(hex)   { cp.first = hex.charAt(0); cp.h = hex.length; cp.done = true }\n" +
        "}";

    @Test
    void stringMethodsOnColorHexArgWork() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item cp = v.load(QML);
        flush(v);

        // A non-colour string works fine through the raw path.
        MethodInvocation.callMethod(cp, "fRaw", new Object[]{"hello"});
        flush(v);
        assertEquals(Boolean.TRUE, peek(cp, "done"));
        assertEquals("h", peek(cp, "first"));
        assertEquals(5.0, ((Number) peek(cp, "h")).doubleValue(), 1e-9);

        // A colour-hex string is wrapped as JsColor but must still expose charAt/length.
        set(cp, "done", Boolean.FALSE);
        set(cp, "first", "");
        MethodInvocation.callMethod(cp, "fRaw", new Object[]{"#ff33aa55"});
        flush(v);
        assertEquals(Boolean.TRUE, peek(cp, "done"),
            "charAt on a colour-hex String must not silently abort the function");
        assertEquals("#", peek(cp, "first"), "charAt(0) resolves against the hex string");
        assertEquals(9.0, ((Number) peek(cp, "h")).doubleValue(), 1e-9, "length is the hex length");
    }
}
