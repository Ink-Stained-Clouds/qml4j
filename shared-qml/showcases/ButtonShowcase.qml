import QtQuick
import md3.Core

// Real MD3 Button, unmodified: elevated / filled / filledTonal / outlined / text.
// Tap one -- ripple + state layer; the elevated type casts a drop shadow.
Rectangle {
    id: root
    x: 0
    y: 0
    color: "#fef7ff"

    Text {
        x: 16; y: 12
        text: "MD3 Button (elevated / filled / tonal / outlined / text):"
        color: "#1d1b20"; fontSize: 24; width: parent.width - 32; height: 32
    }

    Column {
        x: 16; y: 60
        spacing: 16

        Button { type: "elevated";    text: "Elevated";  icon: "add" }
        Button { type: "filled";      text: "Filled" }
        Button { type: "filledTonal"; text: "Tonal" }
        Button { type: "outlined";    text: "Outlined" }
        Button { type: "text";        text: "Text" }
    }
}
