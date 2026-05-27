package io.qml4j.render.items;

import io.qml4j.engine.binding.Property;

public class State extends Item {
    public final Property<String> name = new Property<>(null);

    public State() {
        visible.set(Boolean.FALSE);
    }

    public void apply() {
        for (Item c : children) {
            if (c instanceof PropertyChanges) ((PropertyChanges) c).apply();
        }
    }

    public void revert() {
        for (Item c : children) {
            if (c instanceof PropertyChanges) ((PropertyChanges) c).revert();
        }
    }
}
