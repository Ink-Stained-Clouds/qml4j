package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.Property;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImplicitSizeTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    private static Object readProp(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return ((Property<?>) f.get(o)).peek();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void implicitWidthIsReadableBindableProperty() {
        Item root = newView().load(
            "Item {\n" +
            "  implicitWidth: 42\n" +
            "  implicitHeight: 24\n" +
            "  property int iw: implicitWidth\n" +
            "  property int ih: implicitHeight\n" +
            "}");
        assertEquals(42L, ((Number) root.implicitWidth.peek()).longValue());
        assertEquals(24L, ((Number) root.implicitHeight.peek()).longValue());
        assertEquals(42L, readProp(root, "iw"));
        assertEquals(24L, readProp(root, "ih"));
    }

    @Test
    void implicitWidthBindsToExpressionAndReacts() {
        Item root = newView().load(
            "Item {\n" +
            "  property int base: 10\n" +
            "  implicitWidth: base * 3\n" +
            "}");
        assertEquals(30L, ((Number) root.implicitWidth.peek()).longValue());
        // mutate base -> implicitWidth re-evaluates
        @SuppressWarnings("unchecked")
        Property<Object> base;
        try {
            Field f = root.getClass().getField("base");
            base = (Property<Object>) f.get(root);
        } catch (Exception e) { throw new RuntimeException(e); }
        base.set(20);
        assertEquals(60L, ((Number) root.implicitWidth.peek()).longValue());
    }

    @Test
    void childImplicitWidthReadableByParent() {
        Item root = newView().load(
            "Item {\n" +
            "  property int childIw: inner.implicitWidth\n" +
            "  Item { id: inner; implicitWidth: 77 }\n" +
            "}");
        assertEquals(77L, readProp(root, "childIw"));
    }

    @Test
    void explicitWidthIndependentOfImplicit() {
        Item root = newView().load(
            "Item { width: 100; implicitWidth: 999 }");
        assertEquals(100L, root.width.peek().longValue());
        assertEquals(999L, ((Number) root.implicitWidth.peek()).longValue());
    }
}
