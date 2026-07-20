package io.github.timer_err.qml4j.compat;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.view.Loader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// G17: Loader.source must resolve relative to the document that declares the
// Loader (Qt semantics), not the resource root. MD3's Menu.qml builds recursive
// submenus with `source: "Menu.qml"` (its own file, unmodified upstream); with
// root-relative resolution that load silently fails and submenus never appear.
class MenuSubmenuE2ETest {

    private static final String HARNESS =
        "import QtQuick\n" +
        "import md3.Core\n" +
        "Item {\n" +
        "  id: sceneRoot\n" +
        "  width: 640; height: 480\n" +
        "  Item { id: anchorItem; x: 100; y: 50; width: 40; height: 20 }\n" +
        "  Menu {\n" +
        "    id: menu\n" +
        "    model: [\n" +
        "      { text: \"Copy\" },\n" +
        "      { text: \"Share\", subItems: [ { text: \"Email\" }, { text: \"Link\" } ] }\n" +
        "    ]\n" +
        "  }\n" +
        "  Component.onCompleted: menu.open(anchorItem, 0, 0)\n" +
        "}\n";

    @AfterEach
    void resetTheme() {
        OffscreenCompat.setTheme(false, "#6750A4");
    }

    @Test
    void recursiveSubmenuLoaderResolvesAgainstDefiningDocument() throws Exception {
        assumeTrue(skijaLoads(), "Skija natives not on test classpath");
        ClasspathResources res = ClasspathResources.md3Core("Menu.qml");
        QmlView v = OffscreenCompat.view(res);
        OffscreenCompat.setTheme(false, "#6750A4");
        v.load(HARNESS, "");
        OffscreenCompat.flush(v);

        // Render a few frames so the Renderer resolves delegate Loaders.
        Path shot = Paths.get("target", "e2e-shots", "menu-open.png");
        OffscreenCompat.shot(v, 640, 480, false, 10, shot);

        List<Loader> submenuLoaders = new ArrayList<>();
        for (Item it : OffscreenCompat.flatten(v.root())) {
            if (it instanceof Loader && "Menu.qml".equals(((Loader) it).source.peek())) {
                submenuLoaders.add((Loader) it);
            }
        }
        assertFalse(submenuLoaders.isEmpty(),
            "menu items must exist (delegate Loaders with source: \"Menu.qml\")");

        Loader active = null;
        Loader inactive = null;
        for (Loader l : submenuLoaders) {
            if (Boolean.TRUE.equals(l.active.peek())) active = l;
            else inactive = l;
        }
        assertNotNull(active, "the Share item has subItems, its submenu Loader must be active");
        assertNotNull(active.loadedItem,
            "G17: active submenu Loader must load Menu.qml relative to md3/Core/ "
            + "(currently resolves against the resource root and silently fails)");
        Object model = readProp(active.loadedItem, "model");
        assertNotNull(model, "onLoaded must have injected subItems into the loaded submenu");
        if (inactive != null) {
            assertNull(inactive.loadedItem, "items without subItems must not load a submenu");
        }

        // Click the Share row (second 48px row of the popup at ~(100,50)): its Ripple
        // calls subMenuLoader.item.open(...), which was a silent no-op while the item
        // was null. The submenu overlay must become visible and change pixels.
        v.dispatchClick(156, 130);
        OffscreenCompat.flush(v);
        Path shotOpen = Paths.get("target", "e2e-shots", "menu-submenu-open.png");
        OffscreenCompat.shot(v, 640, 480, false, 10, shotOpen);

        Item submenuOverlay = (Item) readField(active.loadedItem, "overlayLayer");
        assertNotNull(submenuOverlay, "submenu Menu must expose its overlayLayer id");
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE,
            readProp(submenuOverlay, "visible"),
            "clicking the parent row must open the recursive submenu");
        double delta = OffscreenCompat.meanAbsDelta(shot, shotOpen);
        org.junit.jupiter.api.Assertions.assertTrue(delta > 0.5,
            "submenu popup must change the frame; delta=" + delta);
    }

    private static Object readField(Item item, String name) {
        try {
            return item.getClass().getField(name).get(item);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object readProp(Item item, String name) {
        try {
            java.lang.reflect.Field f = item.getClass().getField(name);
            Object v = f.get(item);
            return v instanceof Property ? ((Property<?>) v).peek() : v;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean skijaLoads() {
        try {
            Class.forName("io.github.humbleui.skija.impl.Library");
            io.github.humbleui.skija.impl.Library.staticLoad();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
