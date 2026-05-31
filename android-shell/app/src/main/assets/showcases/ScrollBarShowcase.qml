import QtQuick
import md3.Core

// Loads the UNMODIFIED third-party MD3 ScrollBar.qml. Flick the list: the
// thumb tracks contentY and fades in while moving (no engine-specific code).
Rectangle {
    x: 60
    y: 10120
    width: 880
    height: 320
    color: "#1c1c28"

    Text {
        x: 16
        y: 12
        text: "MD3 ScrollBar (real third-party component, unmodified):"
        color: "#ffffff"
        fontSize: 24
        width: 820
        height: 32
    }

    Flickable {
        id: fl
        x: 16
        y: 56
        width: 420
        height: 240
        contentWidth: 420
        contentHeight: 2000
        clip: true

        Column {
            spacing: 6
            Repeater {
                model: 40
                Rectangle {
                    width: 420
                    height: 42
                    color: index % 2 === 0 ? "#2a3a52" : "#3a2a3a"
                    Text {
                        x: 12
                        y: 6
                        text: "row " + index
                        color: "#c0d0ff"
                        fontSize: 22
                        width: 380
                        height: 30
                    }
                }
            }
        }
    }

    ScrollBar {
        target: fl
        orientation: Qt.Vertical
        // Widen the (transparent) track so the touch target is finger-sized;
        // the thumb stays thin and centred. ScrollBar.qml is unmodified.
        width: 32
        x: fl.x + fl.width - width
        y: fl.y
        height: fl.height
    }
}
