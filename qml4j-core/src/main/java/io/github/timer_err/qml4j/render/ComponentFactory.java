package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.core.Item;

public interface ComponentFactory {
    // baseDir is the importing document's directory (relative to the resource root),
    // used to resolve relative file imports (import "../widgets"). Empty for the root.
    Item create(String qmlSource, String baseDir);
}
