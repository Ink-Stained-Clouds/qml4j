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
}
