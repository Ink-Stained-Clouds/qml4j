package io.qml4j.render.items;

import io.qml4j.engine.Signal;
import io.qml4j.engine.binding.Property;

public class MouseArea extends Item {
    public final Signal clicked = new Signal();
    // Qt names both a bool property `pressed` and a press signal `pressed(mouse)`.
    // Java can't share the field name, so the signal lives under pressedSignal;
    // the onPressed handler resolves to it via the <name>Signal fallback.
    public final Signal pressedSignal = new Signal();
    public final Signal released = new Signal();
    public final Signal positionChanged = new Signal();
    public final Signal entered = new Signal();
    public final Signal exited = new Signal();
    public final Signal canceled = new Signal();
    // No wheel events on a touch surface; accepted so onWheel handlers compile.
    public final Signal wheel = new Signal();

    public final Property<Boolean> pressed = new Property<>(Boolean.FALSE);
    public final Property<Boolean> hoverEnabled = new Property<>(Boolean.FALSE);
    public final Property<Number> acceptedButtons = new Property<>(1); // Qt.LeftButton
    public final Property<Boolean> propagateComposedEvents = new Property<>(Boolean.FALSE);
    // Accepted for source compatibility; we have no Flickable-style event-steal
    // arbitration to suppress, so the flag is inert.
    public final Property<Boolean> preventStealing = new Property<>(Boolean.FALSE);
    // Accepted for source compatibility; no cursor on a touch surface.
    public final Property<Number> cursorShape = new Property<>(0);
    public final Property<Boolean> containsMouse = new Property<>(Boolean.FALSE);
    public final Property<Number> mouseX = new Property<>(0);
    public final Property<Number> mouseY = new Property<>(0);

    public final Drag drag = new Drag();
}
