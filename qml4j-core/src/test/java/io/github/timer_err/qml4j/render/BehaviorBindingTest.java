package io.github.timer_err.qml4j.render;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

// A `Behavior on X` over a property that carries a binding must still reach the bound
// value: a re-delivered identical target must not restart (and freeze) the animation.
class BehaviorBindingTest {
    private static Item find(Item n, java.util.function.Predicate<Item> p) {
        if (p.test(n)) return n;
        for (Item c : n.children) { Item r = find(c, p); if (r!=null) return r; }
        return null;
    }
    @Test void behaviorOnBoundLeftMargin() {
        Map<String,byte[]> res = new HashMap<>();
        res.put("mymod/qmldir", "Knob 1.0 Knob.qml\nRail 1.0 Rail.qml\n".getBytes(StandardCharsets.UTF_8));
        res.put("mymod/Knob.qml", "import QtQuick\nItem { id: control; implicitWidth: 40; implicitHeight: 40 }\n".getBytes(StandardCharsets.UTF_8));
        res.put("mymod/Rail.qml",
            ("import QtQuick\nimport QtQuick.Layouts\n"
           + "Item { id: r; implicitWidth: 80; property Component header: null\n"
           + "  ColumnLayout { anchors.fill: parent; spacing: 0\n"
           + "    Loader { Layout.fillWidth: true; sourceComponent: r.header }\n"
           + "    Item { Layout.fillHeight: true } } }\n").getBytes(StandardCharsets.UTF_8));
        String qml = "import QtQuick\nimport QtQuick.Layouts\nimport mymod\n"
          + "Item { id: root; property bool isRail: true\n"
          + "  Rail { width: 80; height: 200\n"
          + "    header: Component {\n"
          + "      ColumnLayout { width: parent.width; spacing: 0\n"
          + "        Item { Layout.fillWidth: true; Layout.preferredHeight: 64\n"
          + "          Knob { id: inner\n"
          + "            anchors.verticalCenter: parent.verticalCenter\n"
          + "            anchors.left: parent.left\n"
          + "            anchors.leftMargin: isRail ? (parent.width - width) / 2 : 12\n"
          + "            Behavior on anchors.leftMargin { NumberAnimation { duration: 200 } }\n"
          + "          } } } } } }\n";
        QmlView v = QmlView.withStockTypes(new QmlEngine()); v.resources(res::get);
        Item root = v.load(qml);
        DirtyQueue dq = v.dirtyQueue(); dq.install();
        try { root.width.set(300); root.height.set(400);
            for (int i=0;i<40;i++) { v.renderer().layoutOnly(root); dq.flush(); v.tickAnimations(i * 20_000_000L); }
        } finally { dq.uninstall(); }
        Item inner = find(root, it -> it.width.peekDouble()==40.0);
        assertEquals(20.0, inner==null?-999:inner.x.peekDouble(), 1.0);
    }
}
