package io.qml4j.render.items.animation;

public class OpacityAnimation extends PropertyAnimation {

    @Override
    protected String effectiveProperty() {
        String p = property.peek();
        return p != null ? p : "opacity";
    }
}
