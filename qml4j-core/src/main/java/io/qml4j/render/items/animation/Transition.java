package io.qml4j.render.items.animation;
import io.qml4j.render.items.core.Item;

import io.qml4j.engine.binding.Property;

public class Transition extends Item {
    public final Property<String> from = new Property<>("*");
    public final Property<String> to = new Property<>("*");

    public Transition() {
        visible.set(Boolean.FALSE);
    }

    public boolean matches(String fromName, String toName) {
        return wildcardMatch(from.peek(), fromName)
            && wildcardMatch(to.peek(), toName);
    }

    private static boolean wildcardMatch(String pattern, String name) {
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) return true;
        return pattern.equals(name == null ? "" : name);
    }
}
