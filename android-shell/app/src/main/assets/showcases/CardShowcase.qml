import QtQuick
import md3.Core

// Real MD3 Card: elevated (drop shadow via MultiEffect.shadowEnabled), filled, outlined.
Rectangle {
    id: root
    x: 0
    y: 12500
    width: parent.width
    height: 260
    color: "#fef7ff"

    Text { x: 16; y: 12; text: "MD3 Card (elevated shadow / filled / outlined):"
        color: "#1d1b20"; fontSize: 24; width: parent.width - 32; height: 32 }

    Column {
        x: 16; y: 56
        spacing: 14

        Card {
            type: "elevated"; width: root.width - 32; height: 56
            Text { anchors.centerIn: parent; text: "Elevated"; color: "#1d1b20"; fontSize: 20 }
        }
        Card {
            type: "filled"; width: root.width - 32; height: 56
            Text { anchors.centerIn: parent; text: "Filled"; color: "#1d1b20"; fontSize: 20 }
        }
        Card {
            type: "outlined"; width: root.width - 32; height: 56
            Text { anchors.centerIn: parent; text: "Outlined"; color: "#1d1b20"; fontSize: 20 }
        }
    }
}
