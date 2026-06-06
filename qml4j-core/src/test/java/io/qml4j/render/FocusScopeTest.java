package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusScopeTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void tabAdvancesInDeclarationOrder() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  Rectangle { id: a; focus: true; activeFocusOnTab: true }\n" +
            "  Rectangle { id: b; activeFocusOnTab: true }\n" +
            "  Rectangle { id: c; activeFocusOnTab: true }\n" +
            "}");
        Item a = root.children.get(0);
        Item b = root.children.get(1);
        assertSame(a, v.focused());
        assertTrue(v.dispatchKey(QmlView.KEY_TAB, null, true));
        assertSame(b, v.focused());
    }

    @Test
    void tabWrapsAtEnd() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  Rectangle { id: a; focus: true; activeFocusOnTab: true }\n" +
            "  Rectangle { id: b; activeFocusOnTab: true }\n" +
            "}");
        Item a = root.children.get(0);
        v.dispatchKey(QmlView.KEY_TAB, null, true);
        v.dispatchKey(QmlView.KEY_TAB, null, true);
        assertSame(a, v.focused());
    }

    @Test
    void backtabGoesBackward() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  Rectangle { id: a; focus: true; activeFocusOnTab: true }\n" +
            "  Rectangle { id: b; activeFocusOnTab: true }\n" +
            "  Rectangle { id: c; activeFocusOnTab: true }\n" +
            "}");
        Item c = root.children.get(2);
        assertTrue(v.dispatchKey(QmlView.KEY_BACKTAB, null, true));
        assertSame(c, v.focused());
    }

    @Test
    void nonTabStopsSkipped() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  Rectangle { id: a; focus: true; activeFocusOnTab: true }\n" +
            "  Rectangle { id: b }\n" +
            "  Rectangle { id: c; activeFocusOnTab: true }\n" +
            "}");
        Item c = root.children.get(2);
        v.dispatchKey(QmlView.KEY_TAB, null, true);
        assertSame(c, v.focused());
    }

    @Test
    void invisibleItemsSkipped() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  Rectangle { id: a; focus: true; activeFocusOnTab: true }\n" +
            "  Rectangle { id: b; visible: false; activeFocusOnTab: true }\n" +
            "  Rectangle { id: c; activeFocusOnTab: true }\n" +
            "}");
        Item c = root.children.get(2);
        v.dispatchKey(QmlView.KEY_TAB, null, true);
        assertSame(c, v.focused());
    }

    @Test
    void tabStaysWithinFocusScope() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  Rectangle { id: outer; activeFocusOnTab: true }\n" +
            "  FocusScope {\n" +
            "    Rectangle { id: a; focus: true; activeFocusOnTab: true }\n" +
            "    Rectangle { id: b; activeFocusOnTab: true }\n" +
            "  }\n" +
            "}");
        Item scope = root.children.get(1);
        Item a = scope.children.get(0);
        Item b = scope.children.get(1);
        assertSame(a, v.focused());
        v.dispatchKey(QmlView.KEY_TAB, null, true);
        assertSame(b, v.focused());
        v.dispatchKey(QmlView.KEY_TAB, null, true);
        assertSame(a, v.focused());
    }

    @Test
    void focusScopeDelegatesToInnerFocusChild() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  FocusScope {\n" +
            "    id: scope\n" +
            "    Rectangle { id: inner; focus: true; activeFocusOnTab: true }\n" +
            "  }\n" +
            "}");
        Item scope = root.children.get(0);
        Item inner = scope.children.get(0);
        v.setFocus(scope);
        assertSame(inner, v.focused());
    }
}
