import QtQuick
import md3.Core

// Real MD3 Checkbox.qml (unmodified): RowLayout + Ripple/MultiEffect + Qt.color.
// Tap a box — it toggles, the ripple expands (masked to the touch target).
Rectangle {
    x: 60
    y: 11680
    width: 880
    height: 240
    color: "#1c1c28"

    Text {
        x: 16; y: 12
        text: "MD3 Checkbox (real component: RowLayout + Ripple/MultiEffect):"
        color: "#ffffff"; fontSize: 24; width: 820; height: 32
    }

    Column {
        x: 16; y: 56
        spacing: 4

        Checkbox { text: "Enable notifications"; checked: true }
        Checkbox { text: "Auto-sync"; checked: false }
        Checkbox { text: "Indeterminate"; indeterminate: true }
    }
}
