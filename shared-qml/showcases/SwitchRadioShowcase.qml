import QtQuick
import md3.Core

// Real MD3 Switch + RadioButton, unmodified (RowLayout + Ripple + Behaviors).
// Tap to toggle: the thumb slides / the dot fills, with ripple feedback.
Rectangle {
    id: root
    x: 0
    y: 0
    color: "#1c1c28"

    Text {
        x: 16; y: 12
        text: "MD3 Switch + RadioButton (real components):"
        color: "#ffffff"; fontSize: 24; width: parent.width - 32; height: 32
    }

    Column {
        x: 16; y: 56
        spacing: 4
        Switch { text: "Wi-Fi"; checked: true }
        Switch { text: "Bluetooth"; checked: false }
    }

    // RadioButton itself isn't a group; the app wires exclusivity (like a
    // ButtonGroup): each binds checked to the shared selection and selects on click.
    property string theme: "Light"

    Column {
        x: 16; y: 150
        spacing: 0
        RadioButton { text: "Light";  checked: root.theme === "Light";  onClicked: root.theme = "Light" }
        RadioButton { text: "Dark";   checked: root.theme === "Dark";   onClicked: root.theme = "Dark" }
        RadioButton { text: "System"; checked: root.theme === "System"; onClicked: root.theme = "System" }
    }
}
