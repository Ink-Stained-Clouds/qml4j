package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FabLoadTest {
    private static byte[] res(String p) {
        try (InputStream in = FabLoadTest.class.getResourceAsStream("/" + p)) {
            assertNotNull(in, "missing " + p); return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Test
    void loadsFab() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir", "Theme.qml", "Ripple.qml", "FAB.qml"})
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 120; height: 120\n  FAB { icon: \"add\"; type: \"standard\" }\n}");
        DirtyQueue dq = v.dirtyQueue();
        dq.install(); try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty());
    }
}
