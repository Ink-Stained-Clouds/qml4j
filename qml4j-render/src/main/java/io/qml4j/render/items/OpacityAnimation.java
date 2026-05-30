package io.qml4j.render.items;

public class OpacityAnimation extends PropertyAnimation {

    @Override
    protected String effectiveProperty() {
        String p = property.peek();
        return p != null ? p : "opacity";
    }
}
