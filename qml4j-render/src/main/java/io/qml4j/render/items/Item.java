package io.qml4j.render.items;

import io.qml4j.render.AnchorLine;
import io.qml4j.render.Anchors;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Item extends QObject {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> width = new Property<>(0);
    public final Property<Number> height = new Property<>(0);
    public final Property<Number> implicitWidth = new Property<>(0);
    public final Property<Number> implicitHeight = new Property<>(0);
    public double lastImplicitWidth = Double.NaN;
    public double lastImplicitHeight = Double.NaN;
    public final Property<Boolean> visible = new Property<>(Boolean.TRUE);
    public final Property<Boolean> enabled = new Property<>(Boolean.TRUE);
    public final Property<Number> opacity = new Property<>(1.0);
    public final Property<Number> rotation = new Property<>(0);
    public final Property<Number> scale = new Property<>(1.0);
    public final Property<Number> z = new Property<>(0);
    public final Property<Boolean> clip = new Property<>(Boolean.FALSE);
    public final Property<Item> parent = new Property<>(null);
    public final List<Item> children = new ArrayList<>();
    public final Anchors anchors = new Anchors();
    public final ChildrenRect childrenRect = new ChildrenRect();
    // QtQuick.Layouts attached props (Layout.fillWidth, Layout.leftMargin, ...).
    public final LayoutAttached Layout = new LayoutAttached();

    public final Property<AnchorLine> left = new Property<>(new AnchorLine(this, AnchorLine.Edge.LEFT));
    public final Property<AnchorLine> right = new Property<>(new AnchorLine(this, AnchorLine.Edge.RIGHT));
    public final Property<AnchorLine> top = new Property<>(new AnchorLine(this, AnchorLine.Edge.TOP));
    public final Property<AnchorLine> bottom = new Property<>(new AnchorLine(this, AnchorLine.Edge.BOTTOM));
    public final Property<AnchorLine> horizontalCenter =
        new Property<>(new AnchorLine(this, AnchorLine.Edge.HORIZONTAL_CENTER));
    public final Property<AnchorLine> verticalCenter =
        new Property<>(new AnchorLine(this, AnchorLine.Edge.VERTICAL_CENTER));

    public final Property<String> state = new Property<>(null);
    public final List<State> states = new ArrayList<>();
    public final List<Transition> transitions = new ArrayList<>();

    public final Property<Boolean> focus = new Property<>(Boolean.FALSE);
    public final Property<Boolean> activeFocus = new Property<>(Boolean.FALSE);
    public final Property<Boolean> activeFocusOnTab = new Property<>(Boolean.FALSE);

    public final Layer layer = new Layer();

    private final StateController stateController = new StateController(this);
    private Keys keys;
    private Consumer<Item> focusHook;

    public Item() {
        state.addListener(stateController::apply);
    }

    public Keys keys() {
        if (keys == null) keys = new Keys();
        return keys;
    }

    public Keys keysOrNull() {
        return keys;
    }

    public void installFocusHook(Consumer<Item> hook) {
        focusHook = hook;
    }

    // Activate declarative State.when bindings once the tree is built.
    public void initStateBindings() {
        if (!states.isEmpty()) stateController.initWhen();
    }

    // QML Item.mapFromItem(source, x, y): map a point from source's coordinate
    // system into this item's. Returns a point ({x, y}); positions only (no
    // rotation/scale), which is what hit-math like Slider's drag needs.
    public Map<String, Object> mapFromItem(Object source, double x, double y) {
        double[] from = source instanceof Item ? ((Item) source).scenePosition() : new double[]{0, 0};
        double[] self = scenePosition();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("x", from[0] + x - self[0]);
        p.put("y", from[1] + y - self[1]);
        return p;
    }

    private double[] scenePosition() {
        double sx = 0, sy = 0;
        for (Item it = this; it != null; it = it.parent.peek()) {
            sx += it.x.peek().doubleValue();
            sy += it.y.peek().doubleValue();
        }
        return new double[]{sx, sy};
    }

    public void forceActiveFocus() {
        Item r = this;
        while (r.parent.peek() != null) r = r.parent.peek();
        if (r.focusHook != null) r.focusHook.accept(this);
    }

    // QML Object.destroy(): detach from the scene. Safe to call from inside an
    // animation tick because the tick walk iterates children in reverse by index.
    public void destroy() {
        Item p = parent.peek();
        if (p != null) p.children.remove(this);
        parent.set(null);
    }
}
