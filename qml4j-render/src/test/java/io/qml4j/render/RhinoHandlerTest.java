package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.Signal;
import io.qml4j.engine.SignalHandler;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;
import io.qml4j.engine.js.RhinoHandler;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Phase 3: signal handler bodies (the for/i++/var/bare-call pain points) run on
// Rhino, not the ASM statement codegen. Confirms the handler is really a
// RhinoHandler AND that its imperative side effects -- loop accumulation and a bare
// call to a QML function -- take effect when the signal fires.
class RhinoHandlerTest {

    private static void flush(QmlView v) {
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
    }

    @SuppressWarnings("unchecked")
    private static List<SignalHandler> handlersOf(Signal s) throws Exception {
        Field f = Signal.class.getDeclaredField("handlers");
        f.setAccessible(true);
        return (List<SignalHandler>) f.get(s);
    }

    private static long propLong(Item root, String name) throws Exception {
        Property<?> p = (Property<?>) root.getClass().getField(name).get(root);
        return ((Number) p.peek()).longValue();
    }

    private static boolean propBool(Item o, String name) throws Exception {
        Property<?> p = (Property<?>) o.getClass().getField(name).get(o);
        return (Boolean) p.peek();
    }

    // Phase 4a: an arrow-form handler `onPressed: (v) => ...` runs on Rhino, with the
    // arrow's params bound to the signal arguments.
    @Test
    void arrowHandlerRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: control\n" +
            "  property int got: 0\n" +
            "  signal pressed(int v)\n" +
            "  onPressed: (v) => { control.got = v * 2 }\n" +
            "}");
        flush(v);

        Signal pressed = (Signal) root.getClass().getField("pressed").get(root);
        assertTrue(handlersOf(pressed).get(0) instanceof RhinoHandler,
            "arrow handler should be a RhinoHandler");

        pressed.emit(21);
        flush(v);
        assertEquals(42, propLong(root, "got"));   // v * 2, v bound to the signal arg
    }

    // Phase 4b: a handler using Qt.callLater runs on Rhino (the canHandle Qt-helper
    // rejection no longer blocks callLater), and the deferred arrow fires on flush.
    @Test
    void callLaterHandlerRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: r\n" +
            "  property int n: 0\n" +
            "  signal go()\n" +
            "  onGo: Qt.callLater(() => { r.n = r.n + 1 })\n" +
            "}");
        flush(v);

        Signal go = (Signal) root.getClass().getField("go").get(root);
        assertTrue(handlersOf(go).get(0) instanceof RhinoHandler);

        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            go.emit();
            assertEquals(0, propLong(root, "n"));   // deferred, not run yet
            dq.flush();
            assertEquals(1, propLong(root, "n"));   // runs on flush
        } finally {
            dq.uninstall();
        }
    }

    // A bare control-flow handler body (no braces) must produce legal wrapped JS --
    // `(function(){ if (...) ... })`, not `(function()if (...) ...)`.
    @Test
    void bareControlFlowHandlerRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  property int n: 0\n" +
            "  property bool gate: true\n" +
            "  signal go()\n" +
            "  onGo: if (gate) n = n + 5\n" +
            "}");
        flush(v);

        Signal go = (Signal) root.getClass().getField("go").get(root);
        assertTrue(handlersOf(go).get(0) instanceof RhinoHandler);

        go.emit();
        flush(v);
        assertEquals(5, propLong(root, "n"));
    }

    // Regression: a handler on a child object that emits the component's signal via a
    // member call (control.clicked()) and writes a component property -- the exact
    // shape of MD3 RadioButton's Ripple.onClicked. The member-form signal emit must
    // resolve to a callable, or the call throws, the handler aborts, and the click is
    // silently lost (the checked write never runs).
    @Test
    void memberSignalEmitFromHandler() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: control\n" +
            "  property bool checked: false\n" +
            "  property int clicks: 0\n" +
            "  signal clicked()\n" +
            "  onClicked: clicks = clicks + 1\n" +
            "  Item {\n" +
            "    id: inner\n" +
            "    signal tapped()\n" +
            "    onTapped: {\n" +
            "      control.clicked()\n" +
            "      if (!control.checked) control.checked = true\n" +
            "    }\n" +
            "  }\n" +
            "}");
        flush(v);

        Item inner = root.children.get(0);
        Signal tapped = (Signal) inner.getClass().getField("tapped").get(inner);
        assertTrue(handlersOf(tapped).get(0) instanceof RhinoHandler);

        tapped.emit();
        flush(v);
        assertTrue(propBool(root, "checked"), "checked should flip true on tap");
        assertEquals(1, propLong(root, "clicks"), "control.clicked() should have emitted");
    }

    @Test
    void signalHandlerBodyRunsOnRhino() throws Exception {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  property int acc: 0\n" +
            "  property int calls: 0\n" +
            "  signal go()\n" +
            "  function bump() { calls = calls + 1 }\n" +
            "  onGo: {\n" +
            "    for (var i = 0; i < 4; i++) acc = acc + i;\n" +
            "    bump();\n" +
            "  }\n" +
            "}");
        flush(v);

        Signal go = (Signal) root.getClass().getField("go").get(root);
        SignalHandler handler = handlersOf(go).get(0);
        assertTrue(handler instanceof RhinoHandler,
            "onGo handler should be a RhinoHandler, was " + handler.getClass().getName());

        go.emit();
        flush(v);
        assertEquals(6, propLong(root, "acc"));    // 0+1+2+3, via for/i++
        assertEquals(1, propLong(root, "calls"));  // bare call bump()

        go.emit();
        flush(v);
        assertEquals(12, propLong(root, "acc"));   // accumulates again
        assertEquals(2, propLong(root, "calls"));
    }
}
