package io.qml4j.render.items;

import io.qml4j.engine.Signal;
import io.qml4j.engine.binding.Property;

public class MouseArea extends Item {
    public final Signal clicked = new Signal();
    public final Signal pressed = new Signal();
    public final Signal released = new Signal();
    public final Signal positionChanged = new Signal();
    public final Signal entered = new Signal();
    public final Signal exited = new Signal();

    public final Property<Boolean> isPressed = new Property<>(Boolean.FALSE);
    public final Property<Boolean> hoverEnabled = new Property<>(Boolean.FALSE);
    public final Property<Boolean> containsMouse = new Property<>(Boolean.FALSE);
    public final Property<Number> mouseX = new Property<>(0);
    public final Property<Number> mouseY = new Property<>(0);

    public final Drag drag = new Drag();
}
