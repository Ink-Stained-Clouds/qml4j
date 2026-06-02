import QtQuick
import "."

// default property alias: the three Rectangles below are written directly inside
// Panel{} yet land in its inner Column (Panel.qml: default property alias
// content: body.children).
Rectangle {
    x: 60
    y: 11380
    width: 880
    height: 240
    color: "#1c1c28"

    Text {
        x: 16; y: 12
        text: "default property alias (children redirected into inner Column):"
        color: "#ffffff"; fontSize: 24; width: 820; height: 32
    }

    Panel {
        x: 16; y: 56
        title: "Panel { } default content"

        Rectangle { width: 120; height: 28; radius: 4; color: "#5070ff" }
        Rectangle { width: 220; height: 28; radius: 4; color: "#50b070" }
        Rectangle { width: 90;  height: 28; radius: 4; color: "#b05070" }
    }
}
