package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Regression: a binding on a compound child that references the enclosing component's
// id -- MD3 Card's `width: root.width - 32`, where the showcase root and Card both use
// `id: root` -- must resolve `root` to the showcase root, not the Card itself. The
// QmlScope was picking up Card's leaked internal `root` field, making the binding
// self-referential (froze at -32). Scene ids are now resolved lexically on the
// component root.
class SceneIdScopeTest {

    private static byte[] res(String path) {
        try (InputStream in = SceneIdScopeTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void cardWidthResolvesEnclosingRootId() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("md3/Core/qmldir", res("md3/Core/qmldir"));
        files.put("md3/Core/Theme.qml", res("md3/Core/Theme.qml"));
        files.put("md3/Core/Ripple.qml", res("md3/Core/Ripple.qml"));
        files.put("md3/Core/Card.qml", res("md3/Core/Card.qml"));

        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\n" +
            "import md3.Core\n" +
            "Rectangle {\n" +
            "  id: root\n" +
            "  width: 1200; height: 400\n" +
            "  Card { id: c; type: \"elevated\"; width: root.width - 32; height: 56 }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }

        Item card = root.children.get(0);
        assertEquals(1168L, ((Number) card.width.peek()).longValue(), "root.width - 32 with root.width=1200");

        root.width.set(900);
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertEquals(868L, ((Number) card.width.peek()).longValue(), "binding stays reactive to root.width");
    }
}
