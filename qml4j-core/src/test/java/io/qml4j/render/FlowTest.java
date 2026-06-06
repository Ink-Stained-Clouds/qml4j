package io.qml4j.render;

import io.qml4j.render.items.layout.Flow;
import io.qml4j.render.items.core.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowTest {
    private static Rectangle rect(double w, double h) {
        Rectangle r = new Rectangle();
        r.width.set(w);
        r.height.set(h);
        return r;
    }

    @Test
    void leftToRightWrapsAtWidthBound() {
        Flow flow = new Flow();
        flow.width.set(100.0);
        flow.spacing.set(10);
        for (int i = 0; i < 3; i++) {
            Rectangle r = rect(40, 20);
            flow.children.add(r);
            r.parent.set(flow);
        }
        flow.layout();

        Rectangle c0 = (Rectangle) flow.children.get(0);
        Rectangle c1 = (Rectangle) flow.children.get(1);
        Rectangle c2 = (Rectangle) flow.children.get(2);
        assertEquals(0.0, c0.x.peek().doubleValue(), 1e-6);
        assertEquals(50.0, c1.x.peek().doubleValue(), 1e-6, "second fits on row 0 after spacing");
        assertEquals(0.0, c2.x.peek().doubleValue(), 1e-6, "third wraps to row 1");
        assertEquals(30.0, c2.y.peek().doubleValue(), 1e-6, "row 1 starts below row 0 + spacing");
        assertEquals(50.0, flow.implicitHeight.peek().doubleValue(), 1e-6);
    }

    @Test
    void topToBottomWrapsAtHeightBound() {
        Flow flow = new Flow();
        flow.flow.set(1);
        flow.height.set(50.0);
        flow.spacing.set(10);
        for (int i = 0; i < 3; i++) {
            Rectangle r = rect(40, 20);
            flow.children.add(r);
            r.parent.set(flow);
        }
        flow.layout();

        Rectangle c2 = (Rectangle) flow.children.get(2);
        assertEquals(50.0, c2.x.peek().doubleValue(), 1e-6, "third wraps to column 1");
        assertEquals(0.0, c2.y.peek().doubleValue(), 1e-6);
    }
}
