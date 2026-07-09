package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.core.Item;

public interface ComponentFactory {
    // baseDir is the importing document's directory (relative to the resource root),
    // used to resolve relative file imports (import "../widgets"). Empty for the root.
    Item create(String qmlSource, String baseDir);

    // Instantiate a Loader's `source` (a resource path). When the path names a file
    // already compiled as a compound type (e.g. a recursive `source: "md3/Core/Menu.qml"`
    // that resolves to the same file as the `Menu` type), the existing compiled class is
    // REUSED rather than recompiling the document into a fresh class set — the latter mints
    // classes the build-time AOT capture never saw, which is fatal under native-image.
    Item createFromSource(String sourcePath);
}
