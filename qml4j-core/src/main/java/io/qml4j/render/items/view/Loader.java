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
        // Qt's Loader sizes its item to fill but does NOT keep pinning x/y -- the item
        // defaults to (0,0) (the fill origin) yet stays free to be offset, e.g. the MD3
        // page switcher animates the new page's y from 50 to 0. Forcing y=0 every layout
        // fought that animation and the page snapped in.
        double w = width.peekDouble();
        double h = height.peekDouble();
        if (w > 0) it.width.set(w);
        if (h > 0) it.height.set(h);
    }
}
