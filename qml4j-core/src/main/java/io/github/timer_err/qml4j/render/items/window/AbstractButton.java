package io.github.timer_err.qml4j.render.items.window;
import io.github.timer_err.qml4j.render.items.core.Item;

import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.engine.binding.Property;

public class AbstractButton extends Control {
    public final Property<String> text = new Property<>("");
    public final Property<Boolean> down = new Property<>(Boolean.FALSE);
    public final Property<Boolean> checkable = new Property<>(Boolean.FALSE);
    public final Property<Boolean> checked = new Property<>(Boolean.FALSE);
    public final Property<Boolean> autoExclusive = new Property<>(Boolean.FALSE);

    public final Signal clicked = new Signal();
    public final Signal pressed = new Signal();
    public final Signal released = new Signal();
    public final Signal toggled = new Signal();

    public void press() {
        down.set(Boolean.TRUE);
        pressed.emit();
    }

    public void releaseInside() {
        down.set(Boolean.FALSE);
        released.emit();
        activate();
        clicked.emit();
    }

    public void releaseOutside() {
        down.set(Boolean.FALSE);
        released.emit();
    }

    private void activate() {
        if (!Boolean.TRUE.equals(checkable.peek())) return;
        if (Boolean.TRUE.equals(autoExclusive.peek())) {
            if (Boolean.TRUE.equals(checked.peek())) return;
            setExclusiveChecked();
        } else {
            checked.set(!Boolean.TRUE.equals(checked.peek()));
            toggled.emit();
        }
    }

    private void setExclusiveChecked() {
        Item p = parent.peek();
        if (p != null) {
            for (Item sib : p.children) {
                if (sib != this && sib instanceof AbstractButton) {
                    AbstractButton b = (AbstractButton) sib;
                    if (Boolean.TRUE.equals(b.autoExclusive.peek())
                        && Boolean.TRUE.equals(b.checked.peek())) {
                        b.checked.set(Boolean.FALSE);
                        b.toggled.emit();
                    }
                }
            }
        }
        checked.set(Boolean.TRUE);
        toggled.emit();
    }
}
