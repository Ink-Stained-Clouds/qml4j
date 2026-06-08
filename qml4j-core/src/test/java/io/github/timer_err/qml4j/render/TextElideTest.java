package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextElideTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void textElideAcceptsEnum() {
        Item root = newView().load(
            "import QtQuick\n" +
            "Text { text: \"a long label\"; width: 40; elide: Text.ElideRight }");
        try {
            Field f = root.getClass().getField("elide");
            assertEquals(3L, ((Property<?>) f.get(root)).peek());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
