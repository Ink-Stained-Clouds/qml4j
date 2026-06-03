import QtQuick
import md3.Core

// Real MD3 Switch + RadioButton, unmodified (RowLayout + Ripple + Behaviors).
// Tap to toggle: the thumb slides / the dot fills, with ripple feedback.
Rectangle {
    x: 60
    y: 11980
    width: 880
    height: 280
    color: "#1c1c28"

    Text {
        x: 16; y: 12
        text: "MD3 Switch + RadioButton (real components):"
        color: "#ffffff"; fontSize: 24; width: 820; height: 32
    }

    Column {
        x: 16; y: 56
        spacing: 4
        Switch { text: "Wi-Fi"; checked: true }
        Switch { text: "Bluetooth"; checked: false }
    }

    Column {
        x: 440; y: 56
        spacing: 0
        RadioButton { text: "Light"; checked: true }
        RadioButton { text: "Dark"; checked: false }
        RadioButton { text: "System"; checked: false }
    }
}
