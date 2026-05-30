package io.qml4j.render;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.items.Item;
import io.qml4j.render.items.PathArc;
import io.qml4j.render.items.PathCubic;
import io.qml4j.render.items.PathLine;
import io.qml4j.render.items.PathQuad;
import io.qml4j.render.items.Shape;
import io.qml4j.render.items.ShapePath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void shapeHoldsShapePathInElements() {
        Item root = newView().load(
            "Shape {\n" +
            "  ShapePath {\n" +
            "    strokeColor: \"#ff0000\"\n" +
            "    fillColor: \"#00ff00\"\n" +
            "    strokeWidth: 3\n" +
            "    startX: 10; startY: 20\n" +
            "    PathLine { x: 100; y: 20 }\n" +
            "    PathLine { x: 100; y: 80 }\n" +
            "  }\n" +
            "}");
        Shape shape = (Shape) root;
        assertEquals(1, shape.elements.size());
        ShapePath sp = shape.elements.get(0);
        assertEquals("#ff0000", sp.strokeColor.peek());
        assertEquals("#00ff00", sp.fillColor.peek());
        assertEquals(3L, sp.strokeWidth.peek().longValue());
        assertEquals(10L, sp.startX.peek().longValue());
        assertEquals(2, sp.pathElements.size());
        assertInstanceOf(PathLine.class, sp.pathElements.get(0));
        assertEquals(100L, ((PathLine) sp.pathElements.get(0)).x.peek().longValue());
    }

    @Test
    void shapePathDefaultsMatchQt() {
        Item root = newView().load(
            "Shape { ShapePath { PathLine { x: 1; y: 1 } } }");
        ShapePath sp = ((Shape) root).elements.get(0);
        assertEquals("#ffffff", sp.strokeColor.peek());
        assertEquals("#ffffff", sp.fillColor.peek());
        assertEquals(1L, sp.strokeWidth.peek().longValue());
        assertEquals("SquareCap", sp.capStyle.peek());
        assertEquals("BevelJoin", sp.joinStyle.peek());
        assertEquals("OddEvenFill", sp.fillRule.peek());
    }

    @Test
    void allPathElementTypesAccepted() {
        Item root = newView().load(
            "Shape {\n" +
            "  ShapePath {\n" +
            "    startX: 0; startY: 0\n" +
            "    PathLine { x: 10; y: 0 }\n" +
            "    PathQuad { x: 20; y: 10; controlX: 15; controlY: 0 }\n" +
            "    PathCubic { x: 40; y: 10; control1X: 25; control1Y: 20; control2X: 35; control2Y: 0 }\n" +
            "    PathArc { x: 60; y: 10; radiusX: 10; radiusY: 10 }\n" +
            "  }\n" +
            "}");
        ShapePath sp = ((Shape) root).elements.get(0);
        assertEquals(4, sp.pathElements.size());
        assertInstanceOf(PathLine.class, sp.pathElements.get(0));
        assertInstanceOf(PathQuad.class, sp.pathElements.get(1));
        assertInstanceOf(PathCubic.class, sp.pathElements.get(2));
        assertInstanceOf(PathArc.class, sp.pathElements.get(3));
    }

    @Test
    void multipleShapePathsInOneShape() {
        Item root = newView().load(
            "Shape {\n" +
            "  ShapePath { PathLine { x: 1; y: 1 } }\n" +
            "  ShapePath { PathLine { x: 2; y: 2 } }\n" +
            "}");
        assertEquals(2, ((Shape) root).elements.size());
    }

    @Test
    void pathArcExposesArcProperties() {
        Item root = newView().load(
            "Shape {\n" +
            "  ShapePath {\n" +
            "    PathArc { x: 50; y: 50; radiusX: 25; radiusY: 15; useLargeArc: true; direction: \"Counterclockwise\" }\n" +
            "  }\n" +
            "}");
        ShapePath sp = ((Shape) root).elements.get(0);
        PathArc arc = (PathArc) sp.pathElements.get(0);
        assertEquals(25L, arc.radiusX.peek().longValue());
        assertTrue(arc.useLargeArc.peek());
        assertEquals("Counterclockwise", arc.direction.peek());
    }

    @Test
    void quadAndCubicControlPointsBind() {
        Item root = newView().load(
            "Shape {\n" +
            "  ShapePath {\n" +
            "    PathQuad { x: 20; y: 10; controlX: 15; controlY: 5 }\n" +
            "    PathCubic { x: 40; y: 10; control1X: 25; control1Y: 20; control2X: 35; control2Y: 0 }\n" +
            "  }\n" +
            "}");
        ShapePath sp = ((Shape) root).elements.get(0);
        PathQuad q = (PathQuad) sp.pathElements.get(0);
        PathCubic c = (PathCubic) sp.pathElements.get(1);
        assertEquals(15L, q.controlX.peek().longValue());
        assertEquals(35L, c.control2X.peek().longValue());
    }
}
