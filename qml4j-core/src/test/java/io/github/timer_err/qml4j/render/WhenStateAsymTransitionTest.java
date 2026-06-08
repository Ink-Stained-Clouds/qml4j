package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Reproduces the MD3 NavigationBar pill indicator exactly: a child Rectangle
// whose width is 0 in the base state, driven to 64 by a `when:`-bound State,
// with an ASYMMETRIC Transition (from:"" to:"selected" -> animate in, snap out).
// The `when` source is a sibling bool property toggled at runtime (the click).
class WhenStateAsymTransitionTest {

    private static final String SRC =
        "Item {\n" +
        "  id: root\n" +
        "  property bool sel: true\n" +
        "  Rectangle {\n" +
        "    id: pill\n" +
        "    width: 0\n" +
        "    height: 32\n" +
        "    states: State { name: \"selected\"; when: root.sel\n" +
        "      PropertyChanges { target: pill; width: 64 } }\n" +
        "    transitions: Transition { from: \"\"; to: \"selected\"\n" +
        "      NumberAnimation { property: \"width\"; duration: 150 } }\n" +
        "  }\n" +
        "}";

    private long clock = 2_000_000_000L;

    private void tick(QmlView v, long deltaMs) {
        clock += deltaMs * 1_000_000L;
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try {
            v.tickAnimations(clock);
            dq.flush();
        } finally {
            dq.uninstall();
        }
    }

    private void settle(QmlView v) {
        tick(v, 1);
        tick(v, 300);
    }

    private static Item pill(Item root) {
        return root.children.get(0);
    }

    private static double w(Item p) {
        return p.width.peek().doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static void setSel(Item root, boolean value) {
        try {
            Field f = root.getClass().getField("sel");
            ((Property<Object>) f.get(root)).set(value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void initialSelectedShowsPill() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(SRC);
        settle(v);
        assertEquals(64.0, w(pill(root)), 1e-6, "initially selected -> pill width 64");
    }

    @Test
    void deselectThenReselectViaWhen() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load(SRC);
        settle(v);
        assertEquals(64.0, w(pill(root)), 1e-6, "start selected");

        setSel(root, false); settle(v);
        assertEquals(0.0, w(pill(root)), 1e-6, "deselected -> snap to 0");

        setSel(root, true); settle(v);
        assertEquals(64.0, w(pill(root)), 1e-6, "reselected -> animate back to 64");
    }

    // Faithful to NavigationBar.qml: the pill lives in a Repeater delegate whose
    // `selected` is `index === root.currentIndex`. Clicking re-evaluates the
    // delegate bindings; the newly selected delegate must animate its pill in.
    private static final String REPEATER_SRC =
        "Row {\n" +
        "  id: bar\n" +
        "  property int currentIndex: 0\n" +
        "  Repeater {\n" +
        "    model: 3\n" +
        "    Item {\n" +
        "      id: navItem\n" +
        "      width: 64; height: 48\n" +
        "      property bool selected: index === bar.currentIndex\n" +
        "      Rectangle {\n" +
        "        id: pill\n" +
        "        width: 0; height: 32\n" +
        "        states: State { name: \"selected\"; when: navItem.selected\n" +
        "          PropertyChanges { target: pill; width: 64 } }\n" +
        "        transitions: Transition { from: \"\"; to: \"selected\"\n" +
        "          NumberAnimation { property: \"width\"; duration: 150 } }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}";

    @SuppressWarnings("unchecked")
    private static void setCurrentIndex(Item bar, int value) {
        try {
            Field f = bar.getClass().getField("currentIndex");
            ((Property<Object>) f.get(bar)).set(value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // The pill of the Nth nav item. The Repeater node is bar.children[0];
    // the expanded delegates follow it, so delegate N is at index N+1.
    private static double pillWidth(Item bar, int navIndex) {
        Item navItem = bar.children.get(navIndex + 1);
        return navItem.children.get(0).width.peek().doubleValue();
    }

    @Test
    void clickingMovesPillToNewItem() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item bar = v.load(REPEATER_SRC);
        settle(v);
        assertEquals(64.0, pillWidth(bar, 0), 1e-6, "item 0 selected at start");
        assertEquals(0.0, pillWidth(bar, 1), 1e-6, "item 1 not selected");

        setCurrentIndex(bar, 1); settle(v);
        assertEquals(0.0, pillWidth(bar, 0), 1e-6, "item 0 deselected -> 0");
        assertEquals(64.0, pillWidth(bar, 1), 1e-6, "item 1 selected -> pill animates in");
    }
}
