package io.github.timer_err.qml4j.render.items.shape;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.QmlDefaultList;
import io.github.timer_err.qml4j.render.Painter;

import java.util.ArrayList;
import java.util.List;

@QmlDefaultList("elements")
public class Shape extends Item {
    public final List<ShapePath> elements = new ArrayList<>();

    @Override
    public void paint(Painter p, float w, float h, float alpha) {
        p.drawShape(this, alpha);
    }

    @Override
    protected void wireDeferredContentInvalidation() {
        // ShapePath and its PathElements are QObjects (stroke/fill colours, coordinates); wire them
        // so a vector animation re-records this Shape's cache boundary.
        for (int i = 0, n = elements.size(); i < n; i++) wireHolderContent(elements.get(i));
    }
}
