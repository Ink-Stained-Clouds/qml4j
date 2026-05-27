package io.qml4j.render;

import io.qml4j.render.items.Item;

public interface ComponentFactory {
    Item create(String qmlSource);
}
