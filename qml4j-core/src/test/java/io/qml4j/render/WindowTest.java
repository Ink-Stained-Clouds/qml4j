package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.window.ApplicationWindow;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.window.Window;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WindowTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void windowAsRoot() {
        Item root = newView().load(
            "Window {\n" +
            "  width: 320; height: 240\n" +
            "  color: \"#202830\"\n" +
            "  title: \"hi\"\n" +
            "  Rectangle { width: 50; height: 50; color: \"#ff0000\" }\n" +
            "}");
        assertInstanceOf(Window.class, root);
        Window w = (Window) root;
        assertEquals("#202830", w.color.peek());
        assertEquals("hi", w.title.peek());
        assertEquals(1, w.children.size());
    }

    @Test
    void windowColorDefaultsWhite() {
        Item root = newView().load("Window { width: 100; height: 100 }");
        assertEquals("#ffffff", ((Window) root).color.peek());
    }

    @Test
    void applicationWindowHeaderFooter() {
        Item root = newView().load(
            "ApplicationWindow {\n" +
            "  width: 400; height: 300\n" +
            "  header: Rectangle { id: h; height: 40; color: \"#303040\" }\n" +
            "  footer: Rectangle { id: f; height: 30; color: \"#202020\" }\n" +
            "  Rectangle { width: 100; height: 100; color: \"#00ff00\" }\n" +
            "}");
        ApplicationWindow aw = (ApplicationWindow) root;
        assertInstanceOf(io.qml4j.render.items.core.Rectangle.class, aw.header.peek());
        assertInstanceOf(io.qml4j.render.items.core.Rectangle.class, aw.footer.peek());
        assertEquals(1, aw.children.size());
    }

    @Test
    void applicationWindowChromeLayout() {
        Item root = newView().load(
            "ApplicationWindow {\n" +
            "  width: 400; height: 300\n" +
            "  header: Rectangle { height: 40 }\n" +
            "  footer: Rectangle { height: 30 }\n" +
            "}");
        ApplicationWindow aw = (ApplicationWindow) root;
        aw.layoutChrome(400, 300);
        Item hdr = aw.header.peek();
        Item ftr = aw.footer.peek();
        assertEquals(0L, hdr.y.peek().longValue());
        assertEquals(400L, hdr.width.peek().longValue());
        assertEquals(270L, ftr.y.peek().longValue());
        assertEquals(400L, ftr.width.peek().longValue());
    }

    @Test
    void applicationWindowContentBand() {
        Item root = newView().load(
            "ApplicationWindow {\n" +
            "  width: 400; height: 300\n" +
            "  header: Rectangle { height: 40 }\n" +
            "  footer: Rectangle { height: 30 }\n" +
            "}");
        ApplicationWindow aw = (ApplicationWindow) root;
        assertEquals(40f, aw.contentTop());
        assertEquals(270f, aw.contentBottom(300));
    }

    @Test
    void applicationWindowIsAWindow() {
        Item root = newView().load(
            "ApplicationWindow { width: 100; height: 100; color: \"#123456\" }");
        assertInstanceOf(Window.class, root);
        assertEquals("#123456", ((Window) root).color.peek());
    }
}
