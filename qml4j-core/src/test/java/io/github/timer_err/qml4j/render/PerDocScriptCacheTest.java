package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.classloader.ScriptCache;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A document's JS bindings compile into its own class loader's script cache (not a
// global one), so the document's generated JS classes are freed with the document --
// the hot-reload foundation.
class PerDocScriptCacheTest {

    private static Item loadWithBinding() {
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        Item root = v.load("import QtQuick\nItem { width: 10; height: width * 2 + 1 }");
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        return root;
    }

    @Test
    void bindingsCacheOnTheDocumentClassLoader() {
        Item root = loadWithBinding();
        ClassLoader cl = root.getClass().getClassLoader();
        assertTrue(cl instanceof ScriptCache, "document root is loaded by a ScriptCache class loader");
        assertFalse(((ScriptCache) cl).jsScriptCache().isEmpty(),
            "the height binding's compiled script is cached on the document loader");

        // A second document gets its own loader + cache (no global sharing of classes).
        ClassLoader cl2 = loadWithBinding().getClass().getClassLoader();
        org.junit.jupiter.api.Assertions.assertNotSame(cl, cl2, "each document has its own class loader");
    }
}
