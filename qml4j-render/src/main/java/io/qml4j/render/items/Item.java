package io.qml4j.render.items;

import io.qml4j.render.Anchors;

import io.qml4j.engine.binding.Property;
import io.qml4j.engine.QObject;

import java.util.ArrayList;
import java.util.List;

public class Item extends QObject {
    public final Property<Number> x = new Property<>(0);
    public final Property<Number> y = new Property<>(0);
    public final Property<Number> width = new Property<>(0);
    public final Property<Number> height = new Property<>(0);
    public final Property<Boolean> visible = new Property<>(Boolean.TRUE);
    public final Property<Number> opacity = new Property<>(1.0);
    public final Property<Item> parent = new Property<>(null);
    public final List<Item> children = new ArrayList<>();
    public final Anchors anchors = new Anchors();

    public final Property<String> state = new Property<>(null);
    public final List<State> states = new ArrayList<>();
    private State activeState;

    public Item() {
        state.addListener(this::applyState);
    }

    private void applyState(String name) {
        State next = null;
        if (name != null && !name.isEmpty()) {
            for (State s : states) {
                if (name.equals(s.name.peek())) { next = s; break; }
            }
        }
        if (next == activeState) return;
        if (activeState != null) activeState.revert();
        activeState = next;
        if (activeState != null) activeState.apply();
    }
}
