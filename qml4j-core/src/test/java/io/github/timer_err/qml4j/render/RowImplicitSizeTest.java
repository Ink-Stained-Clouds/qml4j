package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.layout.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A Row positioner must publish its content size as implicitWidth/Height (like
// RowLayout/Column), so bindings that read a Row's implicitWidth -- e.g. a
// SegmentedButton segment's `Math.max(contentRow.implicitWidth + 24, 48)` -- size to
// the content rather than reading 0 and collapsing.
class RowImplicitSizeTest {

    @Test
    void rowPublishesContentImplicitSize() {
        Row row = new Row();
        row.spacing.set(4);
        Item a = new Item(); a.width.set(30); a.height.set(10);
        Item b = new Item(); b.width.set(50); b.height.set(20);
        row.children.add(a);
        row.children.add(b);

        row.layout();

        assertEquals(84.0, row.implicitWidth.peek().doubleValue());   // 30 + 4 + 50
        assertEquals(20.0, row.implicitHeight.peek().doubleValue());  // max(10, 20)
    }
}
