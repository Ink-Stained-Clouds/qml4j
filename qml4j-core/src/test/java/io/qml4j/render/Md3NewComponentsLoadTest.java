package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// The MD3 components ported once the engine grew the properties/enums they need
// (ComboBox/Menu/DataTable/...). Each instantiates end to end against the real
// md3/Core sources, locking in that the engine still loads them.
class Md3NewComponentsLoadTest {

    private static final String[] COMPONENTS = {
        "Breadcrumb", "ComboBox", "Menu", "DataTable", "NavigationRail", "SideSheet", "Tabs",
        "TextField", "NavigationDrawer", "DatePicker", "TimePicker",
        "CircularProgress", "LinearProgress", "LoadingIndicator",
        "CanvasBarChart", "CanvasLineChart", "CanvasPieChart"
    };

    private static byte[] res(String path) {
        try (InputStream in = Md3NewComponentsLoadTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, "missing test resource " + path);
            return in.readAllBytes();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // Build the md3.Core resource map from every file the qmldir lists, so a component's
    // cross-references (Ripple, Menu, ...) resolve.
    private static Map<String, byte[]> md3Files() {
        Map<String, byte[]> files = new HashMap<>();
        byte[] qmldir = res("md3/Core/qmldir");
        files.put("md3/Core/qmldir", qmldir);
        for (String line : new String(qmldir, StandardCharsets.UTF_8).split("\n")) {
            String[] parts = line.trim().split("\\s+");
            String file = parts[parts.length - 1];
            if (file.endsWith(".qml")) files.put("md3/Core/" + file, res("md3/Core/" + file));
        }
        return files;
    }

    @Test
    void loadsPortedComponents() {
        Map<String, byte[]> files = md3Files();
        for (String name : COMPONENTS) {
            QmlView v = QmlView.withStockTypes(new QmlEngine());
            v.resources(files::get);
            Item root = v.load("import QtQuick\nimport md3.Core\nItem { " + name + " { } }\n");
            DirtyQueue dq = v.dirtyQueue();
            dq.install();
            try { dq.flush(); } finally { dq.uninstall(); }
            assertFalse(root.children.isEmpty(), name + " should instantiate");
        }
    }

    @Test
    void loadsMd3GalleryShowcase() {
        Map<String, byte[]> files = md3Files();
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(new String(res("showcases/Md3GalleryShowcase.qml"), StandardCharsets.UTF_8));
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "MD3 gallery showcase should instantiate");
    }

    @Test
    void loadsChartShowcase() {
        Map<String, byte[]> files = md3Files();
        QmlView v = QmlView.withStockTypes(new QmlEngine());
        v.resources(files::get);
        Item root = v.load(new String(res("showcases/ChartShowcase.qml"), StandardCharsets.UTF_8));
        DirtyQueue dq = v.dirtyQueue();
        dq.install();
        try { dq.flush(); } finally { dq.uninstall(); }
        assertFalse(root.children.isEmpty(), "chart showcase should instantiate");
    }
}
