Rectangle {
    id: fsShowcase
    x: 60
    y: 7240
    width: 880
    height: 420
    color: "#141a22"

    Text {
        x: 16
        y: 16
        text: "M46b FocusScope + Tab order"
        color: "#7ad0ff"
        fontSize: 18
    }

    Text {
        x: 16
        y: 56
        text: "tap a box to focus, then Tab / Backtab cycle in declaration order"
        color: "#aab4c0"
        fontSize: 14
    }

    Rectangle {
        x: 16
        y: 96
        width: 160
        height: 70
        radius: 8
        activeFocusOnTab: true
        color: activeFocus ? "#ff8800" : "#2a3340"
        Text { anchors.centerIn: parent; text: "one"; color: "#ffffff"; fontSize: 16 }
        MouseArea { anchors.fill: parent; onClicked: parent.forceActiveFocus() }
    }

    Rectangle {
        x: 196
        y: 96
        width: 160
        height: 70
        radius: 8
        activeFocusOnTab: true
        color: activeFocus ? "#ff8800" : "#2a3340"
        Text { anchors.centerIn: parent; text: "two"; color: "#ffffff"; fontSize: 16 }
        MouseArea { anchors.fill: parent; onClicked: parent.forceActiveFocus() }
    }

    Rectangle {
        x: 376
        y: 96
        width: 160
        height: 70
        radius: 8
        activeFocusOnTab: true
        color: activeFocus ? "#ff8800" : "#2a3340"
        Text { anchors.centerIn: parent; text: "three"; color: "#ffffff"; fontSize: 16 }
        MouseArea { anchors.fill: parent; onClicked: parent.forceActiveFocus() }
    }

    Text {
        x: 16
        y: 196
        text: "FocusScope below — Tab stays inside it once focus enters"
        color: "#aab4c0"
        fontSize: 14
    }

    Rectangle {
        x: 16
        y: 232
        width: 540
        height: 150
        radius: 10
        color: "#0e1218"
        border.width: 2
        border.color: "#33445a"

        FocusScope {
            x: 20
            y: 20

            Rectangle {
                x: 0
                y: 0
                width: 160
                height: 70
                radius: 8
                activeFocusOnTab: true
                color: activeFocus ? "#33ddaa" : "#23303a"
                Text { anchors.centerIn: parent; text: "scoped A"; color: "#ffffff"; fontSize: 16 }
                MouseArea { anchors.fill: parent; onClicked: parent.forceActiveFocus() }
            }

            Rectangle {
                x: 180
                y: 0
                width: 160
                height: 70
                radius: 8
                activeFocusOnTab: true
                color: activeFocus ? "#33ddaa" : "#23303a"
                Text { anchors.centerIn: parent; text: "scoped B"; color: "#ffffff"; fontSize: 16 }
                MouseArea { anchors.fill: parent; onClicked: parent.forceActiveFocus() }
            }
        }
    }
}
