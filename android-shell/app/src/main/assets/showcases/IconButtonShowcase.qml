import QtQuick
import md3.Core

// Real MD3 IconButton (4 types), unmodified: Ripple + state layers + switch-based
// color logic.
Rectangle {
    x: 0
    y: 12320
    width: parent.width
    height: 160
    color: "#1c1c28"

    Text {
        x: 16; y: 12
        text: "MD3 IconButton (filled / tonal / outlined / standard):"
        color: "#ffffff"; fontSize: 24; width: parent.width - 32; height: 32
    }

    Row {
        x: 16; y: 60
        spacing: 20
        IconButton { icon: "favorite"; type: "filled" }
        IconButton { icon: "settings"; type: "filledTonal" }
        IconButton { icon: "search";   type: "outlined" }
        IconButton { icon: "menu";     type: "standard" }
    }
}
