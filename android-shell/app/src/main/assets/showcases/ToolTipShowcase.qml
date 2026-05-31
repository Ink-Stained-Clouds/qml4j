import QtQuick
import md3.Core

// Loads the UNMODIFIED MD3 ToolTip.qml. Tap the button: tooltip fades in
// (NumberAnimation, from = current opacity) and auto-hides after its timeout.
Rectangle {
    x: 60
    y: 10500
    width: 880
    height: 220
    color: "#1c1c28"

    Text {
        x: 16
        y: 12
        text: "MD3 ToolTip (real third-party component, unmodified):"
        color: "#ffffff"
        fontSize: 24
        width: 820
        height: 32
    }

    Rectangle {
        id: trigger
        x: 16
        y: 64
        width: 240
        height: 64
        radius: 12
        color: area.pressed ? "#5070ff" : "#404060"

        Text {
            anchors.centerIn: parent
            text: "tap for tooltip"
            color: "#ffffff"
            fontSize: 22
        }

        MouseArea {
            id: area
            anchors.fill: parent
            onClicked: tip.open()
        }

        ToolTip {
            id: tip
            text: "Saved to your library"
            y: trigger.height + 8
        }
    }
}
