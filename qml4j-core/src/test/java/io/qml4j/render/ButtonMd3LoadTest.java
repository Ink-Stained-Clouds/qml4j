package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.Item;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ButtonMd3LoadTest {
    private static byte[] res(String p) {
        try (InputStream in = ButtonMd3LoadTest.class.getResourceAsStream("/" + p)) {
            assertNotNull(in, "missing " + p); return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Test
    void loadsButton() {
        Map<String, byte[]> files = new HashMap<>();
        for (String f : new String[]{"qmldir","Theme.qml","Ripple.qml","Button.qml"})
            files.put("md3/Core/" + f, res("md3/Core/" + f));
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(
            "import QtQuick\nimport md3.Core\n" +
            "Item { width: 320; height: 80\n" +
            "  Button { type: \"filled\"; text: \"OK\" }\n" +
            "}");
        DirtyQueue dq = v.dirtyQueue(); dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty());
        Item b = root.children.get(0);
        try {
            java.lang.reflect.Field f = b.getClass().getField("containerColor");
            Object cc = ((io.qml4j.engine.binding.Property<?>) f.get(b)).peek();
            assertEquals("#6750a4", cc, "filled -> primary");
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
