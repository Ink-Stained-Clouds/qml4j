package io.github.timer_err.qml4j.render.items.view;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.engine.Signal;

public class Loader extends Item {
    public final Property<String> source = new Property<>(null);
    public final Property<Component> sourceComponent = new Property<>(null);
    public final Property<Item> item = new Property<>(null);
    public final Property<Boolean> active = new Property<>(Boolean.TRUE);
    @SuppressWarnings("unused")
    public final Property<Boolean> asynchronous = new Property<>(Boolean.FALSE);
    public final Signal loaded = new Signal();

    public String loadedSource;
    public Item loadedItem;
    public Component loadedComponent;

    // Directory (resource-root-relative) of the document that declared this Loader,
    // stamped by the compiler at construction. Qt resolves a relative `source`
    // against the declaring file's directory; a parent-chain walk can't recover it
    // because popups reparent onto the scene root (MD3 Menu). Null in hand-built trees.
    public String documentDir;

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
