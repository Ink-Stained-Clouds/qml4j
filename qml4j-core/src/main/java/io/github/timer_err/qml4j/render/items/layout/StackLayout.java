package io.github.timer_err.qml4j.render.items.layout;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;

// QtQuick.Layouts StackLayout: children are stacked; only the one at currentIndex
// is visible, and it fills the layout's box. implicitWidth/Height track the largest
// child so a StackLayout can content-size when not given an explicit/anchored size.
public class StackLayout extends Item {
    public final Property<Number> currentIndex = new Property<>(0);


    @Override
    public void layout() {
        int cur = currentIndex.peekInt();
        double w = width.peekDouble();
        double h = height.peekDouble();
        double iw = 0;
        double ih = 0;
        int i = 0;
        for (Item c : children) {
            iw = Math.max(iw, c.implicitWidth.peekDouble());
            ih = Math.max(ih, c.implicitHeight.peekDouble());
            boolean show = i == cur;
            c.visible.set(show);
            if (show) {
                c.x.set(0);
                c.y.set(0);
                c.width.set(w);
                c.height.set(h);
            }
            i++;
        }
        implicitWidth.set(iw);
        implicitHeight.set(ih);
    }
}
