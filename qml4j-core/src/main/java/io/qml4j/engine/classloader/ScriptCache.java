package io.qml4j.engine.classloader;

import java.util.Map;

// A per-document JS script cache carried by the document's class loader. Compiled JS
// (Rhino Script objects) is cached here keyed by source, so a document's generated JS
// classes live and die with its class loader -- dropping the document frees them
// (the hot-reload foundation), instead of a global cache pinning every source forever.
public interface ScriptCache {
    Map<String, Object> jsScriptCache();
}
