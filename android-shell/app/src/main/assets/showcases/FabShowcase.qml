import QtQuick
import md3.Core

// Real MD3 FAB: standard / small / large / extended (Ripple + elevation shadow).
Rectangle {
    x: 0
    y: 12820
    width: parent.width
    height: 180
    color: "#fef7ff"

    Text { x: 16; y: 12; text: "MD3 FAB (standard / small / large / extended):"
        color: "#1d1b20"; fontSize: 24; width: parent.width - 32; height: 32 }

    Row {
        x: 16; y: 64
        spacing: 14
        FAB { icon: "add"; type: "standard" }
        FAB { icon: "edit"; type: "small" }
        FAB { icon: "favorite"; type: "large" }
        FAB { icon: "add"; text: "Compose"; type: "extended" }
    }
}
