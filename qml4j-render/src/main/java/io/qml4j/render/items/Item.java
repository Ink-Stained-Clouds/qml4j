package io.qml4j.render.items;

import io.qml4j.render.AnchorLine;
import io.qml4j.render.Anchors;

import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.List;

public class Item extends QObject {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> width = new Property<>(0);
    public final Property<Number> height = new Property<>(0);
    public final Property<Boolean> visible = new Property<>(Boolean.TRUE);
    public final Property<Number> opacity = new Property<>(1.0);
    public final Property<Number> rotation = new Property<>(0);
    public final Property<Number> scale = new Property<>(1.0);
    public final Property<Number> z = new Property<>(0);
    public final Property<Boolean> clip = new Property<>(Boolean.FALSE);
    public final Property<Item> parent = new Property<>(null);
    public final List<Item> children = new ArrayList<>();
    public final Anchors anchors = new Anchors();

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

    private final StateController stateController = new StateController(this);

    public Item() {
        state.addListener(stateController::apply);
    }
}
