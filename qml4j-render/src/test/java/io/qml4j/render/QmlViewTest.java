package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QmlViewTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void loadRectangle() {
        Item root = newView().load("Rectangle { width: 100; height: 50; color: \"#ff0000\" }");
        assertTrue(root instanceof Rectangle);
        Rectangle r = (Rectangle) root;
        assertEquals(100L, r.width.peek().longValue());
        assertEquals(50L, r.height.peek().longValue());
        assertEquals("#ff0000", r.color.peek());
    }

    @Test
    void nestedRectangleBindsToParent() {
        Item root = newView().load(
            "Item {\n" +
            "  width: 200\n" +
            "  height: 100\n" +
            "  Rectangle {\n" +
            "    width: parent.width / 2\n" +
            "    height: parent.height\n" +
            "    color: \"#00ff00\"\n" +
            "  }\n" +
            "}");
        assertEquals(1, root.children.size());
        Rectangle r = (Rectangle) root.children.get(0);
        assertEquals(100L, r.width.peek().longValue());
        assertEquals(100L, r.height.peek().longValue());
        assertSame(root, r.parent.peek());
        root.width.set(400);
        assertEquals(200L, r.width.peek().longValue());
    }

    @Test
    void textType() {
        Item root = newView().load("Text { text: \"hi\"; fontSize: 20 }");
        Text t = (Text) root;
        assertEquals("hi", t.text.peek());
        assertEquals(20L, t.fontSize.peek().longValue());
    }

    @Test
    void columnLaysOutChildren() {
        Item root = newView().load(
            "Column {\n" +
            "  spacing: 5\n" +
            "  Rectangle { width: 10; height: 20 }\n" +
            "  Rectangle { width: 30; height: 15 }\n" +
            "  Rectangle { width: 8;  height: 7  }\n" +
            "}");
        Column col = (Column) root;
        assertEquals(3, col.children.size());
        col.layout();
        assertEquals(0.0, col.children.get(0).y.peek().doubleValue(), 1e-9);
        assertEquals(25.0, col.children.get(1).y.peek().doubleValue(), 1e-9);
        assertEquals(45.0, col.children.get(2).y.peek().doubleValue(), 1e-9);
        assertEquals(52.0, col.height.peek().doubleValue(), 1e-9);
        assertEquals(30L, col.width.peek().longValue());
    }

    @Test
    void rootMustExtendItem() {
        QmlView v = newView();
        assertNotNull(v.load("Item {}"));
    }

    @Test
    void mouseAreaClickHandlerFiresAndAssigns() {
        QmlView v = newView();
        Item root = v.load(
            "Rectangle {\n" +
            "  width: 200; height: 200\n" +
            "  color: \"#ff0000\"\n" +
            "  MouseArea {\n" +
            "    width: parent.width\n" +
            "    height: parent.height\n" +
            "    onClicked: parent.color = \"#00ff00\"\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root;
        assertEquals("#ff0000", r.color.peek());
        assertTrue(v.dispatchClick(10, 10));
        assertEquals("#00ff00", r.color.peek());
    }

    @Test
    void mouseAreaClickMissesOutsideBounds() {
        QmlView v = newView();
        v.load(
            "Item {\n" +
            "  width: 100; height: 100\n" +
            "  MouseArea { width: 50; height: 50; onClicked: parent.width = 999 }\n" +
            "}");
        // miss
        assertEquals(false, v.dispatchClick(80, 80));
        assertEquals(100L, v.root().width.peek().longValue());
        // hit
        assertTrue(v.dispatchClick(10, 10));
        assertEquals(999L, v.root().width.peek().longValue());
    }

    @Test
    void anchorsFillSizesToParentMinusMargins() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  width: 200; height: 100\n" +
            "  Rectangle {\n" +
            "    anchors.fill: parent\n" +
            "    anchors.margins: 10\n" +
            "    color: \"#ff0000\"\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root.children.get(0);
        v.renderer();
        Renderer.applyAnchors(r);
        assertEquals(10.0, r.x.peek().doubleValue(), 1e-6);
        assertEquals(10.0, r.y.peek().doubleValue(), 1e-6);
        assertEquals(180.0, r.width.peek().doubleValue(), 1e-6);
        assertEquals(80.0, r.height.peek().doubleValue(), 1e-6);
    }

    @Test
    void anchorsCenterInCentersChild() {
        QmlView v = newView();
        Item root = v.load(
            "Item {\n" +
            "  width: 200; height: 100\n" +
            "  Rectangle {\n" +
            "    width: 40; height: 20\n" +
            "    anchors.centerIn: parent\n" +
            "    color: \"#00ff00\"\n" +
            "  }\n" +
            "}");
        Rectangle r = (Rectangle) root.children.get(0);
        Renderer.applyAnchors(r);
        assertEquals(80.0, r.x.peek().doubleValue(), 1e-6);
        assertEquals(40.0, r.y.peek().doubleValue(), 1e-6);
    }

    @Test
    void imageTypeLoadsSource() {
        QmlView v = newView();
        Item root = v.load(
            "Image { width: 32; height: 32; source: \"foo.png\" }");
        assertTrue(root instanceof Image);
        Image img = (Image) root;
        assertEquals("foo.png", img.source.peek());
        assertEquals(32L, img.width.peek().longValue());
    }

    @Test
    void parseColorRgb() {
        assertEquals(0xFFFF0000, Renderer.parseColor("#ff0000"));
        assertEquals(0xFF00FF00, Renderer.parseColor("#00ff00"));
        assertEquals(0xFFFFFFFF, Renderer.parseColor("#fff"));
        assertEquals(0x80FF00FF, Renderer.parseColor("#80ff00ff"));
        assertEquals(0xFF000000, Renderer.parseColor(null));
        assertEquals(0xFF000000, Renderer.parseColor("garbage"));
    }
}
