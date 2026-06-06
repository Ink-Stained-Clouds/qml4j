package io.qml4j.render.items.animation;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

public class State extends Item {
    public final Property<String> name = new Property<>(null);
    // Declarative activation: when this binds to true the owner switches to this
    // state (Qt's State.when). null = no when clause (explicit `state:` mode).
    public final Property<Boolean> when = new Property<>(null);

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
