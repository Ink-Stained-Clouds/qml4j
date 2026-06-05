import QtQuick

// Qt.color(str) -> channels, round-tripped through Qt.rgba to derive a faded
// variant; `readonly property` holds the base color.
Rectangle {
    x: 0
    y: 0
    color: "#1c1c28"

    readonly property string base: "#5070ff"

    Text {
        x: 16; y: 12
        text: "Qt.color / Qt.rgba (derive faded variant) + readonly property:"
        color: "#ffffff"; fontSize: 24; width: parent.width - 32; height: 32
    }

    Row {
        x: 16; y: 60
        spacing: 16

        Rectangle {
            width: 160; height: 64; radius: 8
            color: parent.parent.base
            Text { anchors.centerIn: parent; text: "base"; color: "#ffffff"; fontSize: 20 }
        }
        Rectangle {
            width: 160; height: 64; radius: 8
            // half-transparent version of base, built channel-by-channel
            color: Qt.rgba(Qt.color(parent.parent.base).r,
                           Qt.color(parent.parent.base).g,
                           Qt.color(parent.parent.base).b, 0.4)
            Text { anchors.centerIn: parent; text: "alpha 0.4"; color: "#ffffff"; fontSize: 20 }
        }
    }

    // Text.ElideRight: a long string clipped to a fixed width with an ellipsis.
    Text {
        x: 16; y: 140
        width: 360; height: 28
        text: "This caption is far too long to fit in its box"
        elide: Text.ElideRight
        color: "#a0c0ff"; fontSize: 22
    }
}
