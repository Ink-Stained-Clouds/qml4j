Rectangle {
    id: animShowcase
    x: 60
    y: 4240
    width: 880
    height: 440
    color: "#1c1c28"
    state: animShowcase.flipped ? "right" : ""

    property bool flipped: false

    Timer {
        interval: 1400
        repeat: true
        running: true
        onTriggered: animShowcase.flipped = !animShowcase.flipped
    }

    Text {
        x: 16
        y: 12
        text: "M40/M41 typed animations + Behavior-binding interop"
        color: "#ffffff"
        fontSize: 20
        width: 850
        height: 28
    }

    Rectangle {
        id: animColor
        x: 32
        y: 64
        width: 220
        height: 160
        color: "#ff3060"

        Text {
            x: 12; y: 12
            text: "ColorAnimation\n(state-driven)"
            color: "#ffffff"
            fontSize: 16
        }
    }

    Rectangle {
        id: animRotate
        x: 360
        y: 100
        width: 80
        height: 80
        color: "#ffd000"
        rotation: animShowcase.flipped ? 180 : 0

        Behavior on rotation { NumberAnimation { duration: 900 } }
    }

    Text {
        x: 290
        y: 200
        text: "Behavior on rotation"
        color: "#a0c0ff"
        fontSize: 14
        width: 240
        height: 24
    }

    Rectangle {
        id: animOpacity
        x: 560
        y: 64
        width: 240
        height: 160
        color: "#80ff80"
        opacity: animShowcase.flipped ? 1.0 : 0.15

        Behavior on opacity { NumberAnimation { duration: 900 } }

        Text {
            x: 12; y: 12
            text: "Behavior on opacity\n(binding-driven)"
            color: "#000000"
            fontSize: 16
        }
    }

    Rectangle {
        id: animStateBox
        x: 32
        y: 260
        width: 768
        height: 160
        color: "#2a2a3a"

        Rectangle {
            id: stateBall
            x: 16
            y: 24
            width: 80
            height: 80
            color: "#ff80ff"
            rotation: 0
        }

        Text {
            x: 16
            y: 116
            text: "Transition: Number + Color + Rotation"
            color: "#a0c0ff"
            fontSize: 14
            width: 736
            height: 24
        }
    }

    states: [
        State {
            name: "right"
            PropertyChanges { target: animColor; color: "#00c8ff" }
            PropertyChanges { target: stateBall; x: 672; color: "#80ffff"; rotation: 180 }
        }
    ]

    transitions: [
        Transition {
            ColorAnimation { properties: "color"; duration: 900 }
            NumberAnimation { properties: "x,opacity"; duration: 900; easing: "easeOutQuad" }
            RotationAnimation { properties: "rotation"; duration: 900; direction: "Shortest" }
        }
    ]
}
