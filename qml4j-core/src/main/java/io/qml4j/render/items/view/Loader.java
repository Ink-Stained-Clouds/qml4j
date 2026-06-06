package io.qml4j.render.items.view;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;
import io.qml4j.engine.Signal;

public class Loader extends Item {
    public final Property<String> source = new Property<>(null);
    public final Property<Component> sourceComponent = new Property<>(null);
    public final Property<Item> item = new Property<>(null);
    public final Property<Boolean> active = new Property<>(Boolean.TRUE);
    public final Signal loaded = new Signal();

    public String loadedSource;
    public Item loadedItem;
    public Component loadedComponent;

    // Qt Loader sizing: the Loader's implicit size follows its loaded item, and the
    // item fills the Loader's box. Without this a Loader stays 0-sized -- a Loader used
    // as a layout/Repeater delegate (e.g. MD3 Menu items) would collapse to nothing.
    @Override
    public void layout() {
        Item it = loadedItem;
        if (it == null) return;
        double iw = it.implicitWidth.peekDouble();
        double ih = it.implicitHeight.peekDouble();
        if (iw > 0) implicitWidth.set(iw);
        if (ih > 0) implicitHeight.set(ih);
        it.x.set(0);
        it.y.set(0);
        double w = width.peekDouble();
        double h = height.peekDouble();
        if (w > 0) it.width.set(w);
        if (h > 0) it.height.set(h);
    }
}
