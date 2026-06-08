package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SwitchColorBindTest {
    private static Object prop(Object o, String n) {
        try { Field f=o.getClass().getField(n); return ((Property<?>)f.get(o)).peek(); }
        catch(Exception e){ throw new RuntimeException(e);} }

    @Test
    void switchBlockColorDeclared() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(
            "import QtQuick\n" +
            "Item {\n" +
            "  property bool enabled: true\n" +
            "  property string type: \"filled\"\n" +
            "  property color c: {\n" +
            "    if (!enabled) return \"#aaaaaa\"\n" +
            "    switch (type) {\n" +
            "      case \"elevated\": return \"#111111\"\n" +
            "      case \"filled\": return \"#f7f2fa\"\n" +
            "      default: return \"#222222\"\n" +
            "    }\n" +
            "  }\n" +
            "}");
        DirtyQueue dq=v.dirtyQueue(); dq.install(); try{dq.flush();}finally{dq.uninstall();}
        assertEquals("#f7f2fa", prop(root,"c"));
    }
}
