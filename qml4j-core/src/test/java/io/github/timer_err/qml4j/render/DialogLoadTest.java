package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DialogLoadTest {
    private static byte[] res(String p) {
        try (InputStream in = DialogLoadTest.class.getResourceAsStream("/" + p)) {
            assertNotNull(in, "missing " + p); return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Test
    void loadsDialog() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir","Theme.qml","Ripple.qml","Button.qml","Dialog.qml"})
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 400; height: 600\n" +
            "  Dialog { id: dlg; title: \"Delete?\"; text: \"This cannot be undone.\" }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue(); dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty());

        Item dlg = root.children.get(0);
        Item overlay = dlg.children.get(0);
        Item scrim = overlay.children.get(2); // [enterAnim, exitAnim, scrim, wrapper]
        assertFalse(Boolean.TRUE.equals(overlay.visible.peek()), "overlay hidden before open()");

        long clock = 1_000_000_000L;
        // open(): reparents the overlay, shows it, starts the enter animation.
        dq.install();
        try { io.github.timer_err.qml4j.runtime.invoke.MethodInvocation.callMethod(dlg, "open", new Object[0]); dq.flush(); }
        finally { dq.uninstall(); }
        assertTrue(Boolean.TRUE.equals(overlay.visible.peek()), "overlay shown after open()");
        // open() reparents the overlay out of the invisible Dialog onto the page
        // root, else it would never render (its parent control is visible:false).
        assertSame(root, overlay.parent.peek(), "overlay reparented to page root");
        assertTrue(root.children.contains(overlay), "overlay added to root.children");
        assertFalse(dlg.children.contains(overlay), "overlay removed from the Dialog's children");
        for (int i = 0; i < 14; i++) { clock += 16_000_000L; dq.install();
            try { v.tickAnimations(clock); dq.flush(); } finally { dq.uninstall(); } }
        assertTrue(scrim.opacity.peek().doubleValue() > 0.2, "scrim faded in, was " + scrim.opacity.peek());

        // close(): exit animation runs; onFinished hides the overlay.
        dq.install();
        try { io.github.timer_err.qml4j.runtime.invoke.MethodInvocation.callMethod(dlg, "close", new Object[0]); dq.flush(); }
        finally { dq.uninstall(); }
        for (int i = 0; i < 16; i++) { clock += 16_000_000L; dq.install();
            try { v.tickAnimations(clock); dq.flush(); } finally { dq.uninstall(); } }
        assertFalse(Boolean.TRUE.equals(overlay.visible.peek()),
            "overlay hidden after close() exit animation onFinished");
    }
}
