package io.github.timer_err.qml4j.render;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.engine.binding.Property;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StyleManagerTest {
    @Test void resolvesSchemeFromQml() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        String qml = "import QtQuick\n"
          + "Item {\n"
          + "  property string lightPrimary: StyleManager.lightScheme.primary\n"
          + "  property string curSurface: StyleManager.currentScheme.surface\n"
          + "  property bool dark: StyleManager.isDarkTheme\n"
          + "}\n";
        Item root = v.load(qml);
        DirtyQueue dq = v.dirtyQueue(); dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertEquals("#65558f", prop(root, "lightPrimary"));
        assertEquals("#fdf7ff", prop(root, "curSurface")); // light surface (default)
        assertEquals(Boolean.FALSE, prop(root, "dark"));
    }
    private static Object prop(Item root, String name) {
        try { return ((Property<?>) root.getClass().getField(name).get(root)).get(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
