package io.qml4j.engine;

// An object that is not a scene node and so is invisible to the QML `parent` keyword.
// In Qt, animations are QObjects, not Items, so `parent` referenced in a binding written
// inside an animation skips the animation (and any nesting animation groups) and resolves
// to the enclosing visual item's parent. In this engine animations ARE Items, so a read of
// their `parent` must be redirected here to reproduce that transparency -- else a binding
// like `from: -parent.width` reads the zero-sized animation group instead of the bar's
// container.
public interface ParentTransparent {
    Object qmlParent();
}
