import QtQuick
import md3.Core

// Real MD3 Card: elevated (drop shadow via MultiEffect.shadowEnabled), filled, outlined.
Rectangle {
    x: 60
    y: 12500
    width: 880
    height: 260
    color: "#fef7ff"

    Text { x: 16; y: 12; text: "MD3 Card (elevated shadow / filled / outlined):"
        color: "#1d1b20"; fontSize: 24; width: 820; height: 32 }

    Row {
        x: 16; y: 60
        spacing: 24

        Card {
            type: "elevated"; width: 240; height: 160
            Text { anchors.centerIn: parent; text: "Elevated"; color: "#1d1b20"; fontSize: 22 }
        }
        Card {
            type: "filled"; width: 240; height: 160
            Text { anchors.centerIn: parent; text: "Filled"; color: "#1d1b20"; fontSize: 22 }
        }
        Card {
            type: "outlined"; width: 240; height: 160
            Text { anchors.centerIn: parent; text: "Outlined"; color: "#1d1b20"; fontSize: 22 }
        }
    }
}
