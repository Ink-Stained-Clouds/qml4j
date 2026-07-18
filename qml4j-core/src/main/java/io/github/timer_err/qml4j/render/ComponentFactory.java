package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.core.Item;

public interface ComponentFactory {
    // baseDir is the importing document's directory (relative to the resource root),
    // used to resolve relative file imports (import "../widgets"). Empty for the root.
    Item create(String qmlSource, String baseDir);

    // Instantiate a Loader's `source` (a resource path). A relative path resolves
    // against documentDir — the directory of the file that declared the Loader (Qt
    // semantics; MD3 Menu recurses with `source: "Menu.qml"`) — falling back to the
    // resource root for legacy full paths. When the resolved path names a file already
    // compiled as a compound type, the existing compiled class is REUSED rather than
    // recompiling the document into a fresh class set — the latter mints classes the
    // build-time AOT capture never saw, which is fatal under native-image.
    Item createFromSource(String sourcePath, String documentDir);
}
