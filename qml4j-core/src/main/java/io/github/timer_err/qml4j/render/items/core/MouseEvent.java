package io.github.timer_err.qml4j.render.items.core;

// Payload passed to MouseArea signal handlers (onPressed/onPositionChanged/...).
// Mirrors Qt's MouseEvent value type. x/y are local to the MouseArea; button is
// a Qt.MouseButton value so handlers can route on mouse.button.
public final class MouseEvent {
    public static final int LEFT_BUTTON = 1;   // Qt.LeftButton
    public static final int RIGHT_BUTTON = 2;  // Qt.RightButton
    public static final int MIDDLE_BUTTON = 4; // Qt.MiddleButton

    @SuppressWarnings("unused")
    public final double x;
    @SuppressWarnings("unused")
    public final double y;
    @SuppressWarnings("unused")
    public final int button;
    @SuppressWarnings("unused")
    public final int buttons;
    @SuppressWarnings("unused")
    public final int modifiers;
    @SuppressWarnings("unused")
    public boolean accepted = false;

    public MouseEvent(double x, double y) {
        this(x, y, 1, 1, 0);
    }

    public MouseEvent(double x, double y, int button, int buttons, int modifiers) {
        this.x = x;
        this.y = y;
        this.button = button;
        this.buttons = buttons;
        this.modifiers = modifiers;
    }
}
