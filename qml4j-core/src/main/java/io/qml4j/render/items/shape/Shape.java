package io.qml4j.render.items.shape;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.QmlDefaultList;
import io.qml4j.render.Painter;

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
