Rectangle {
    id: root
    color: "#202028"

    Rectangle {
        id: box
        x: 60
        y: 120
        width: 280
        height: 280
        color: "#ff5050"

        states: [
            State {
                name: "big"
                PropertyChanges {
                    target: box
                    width: 720
                    height: 720
                    color: "#5050ff"
                    x: 60
                    y: 120
                }
            }
        ]

        transitions: [
            Transition {
                NumberAnimation {
                    properties: "width,height"
                    duration: 450
                    easing: "easeOutQuad"
                }
            }
        ]

        MouseArea {
            anchors.fill: parent
            onClicked: box.state = box.state === "big" ? "" : "big"
        }

        Text {
            x: 16
            y: 16
            text: "tap to toggle"
            color: "#ffffff"
            fontSize: 28
        }
    }

    Text {
        x: 60
        y: 900
        text: box.state === "big" ? "state: big" : "state: (none)"
        color: "#a0a0c0"
        fontSize: 32
    }

    Rectangle {
        id: badge
        x: 60
        y: 980
        width: 200
        height: 80
        color: "#80ff80"
        opacity: 0.6

        Text {
            x: 16
            y: 24
            text: "opacity 0.6"
            color: "#000000"
            fontSize: 24
        }
    }

    Rectangle {
        id: dot
        x: 400
        y: 980
        width: 80
        height: 80
        color: "#ffcc00"

        Behavior on x { NumberAnimation { duration: 350; easing: "easeOutQuad" } }

        MouseArea {
            anchors.fill: parent
            onClicked: dot.x = dot.x === 400 ? 800 : 400
        }

        Text {
            x: 8
            y: 30
            text: "tap me"
            color: "#000000"
            fontSize: 22
        }
    }
}
