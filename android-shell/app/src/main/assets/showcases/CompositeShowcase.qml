Rectangle {
    id: compositeShowcase
    x: 60
    y: 4720
    width: 880
    height: 480
    color: "#1c1c28"

    property int cycles: 0

    Text {
        x: 16
        y: 12
        text: "M41/M42 composite + PauseAnimation + ScriptAction"
        color: "#ffffff"
        fontSize: 16
    }

    Text {
        x: 520
        y: 12
        text: "cycles: " + compositeShowcase.cycles
        color: "#a0c0ff"
        fontSize: 16
    }

    Rectangle {
        id: pulseBox
        x: 80
        y: 110
        width: 120
        height: 120
        color: "#ff8040"

        SequentialAnimation {
            id: pulse
            ParallelAnimation {
                NumberAnimation { target: pulseBox; property: "width";  from: 120; to: 180; duration: 250; easing: "easeOutQuad" }
                NumberAnimation { target: pulseBox; property: "height"; from: 120; to: 180; duration: 250; easing: "easeOutQuad" }
                ColorAnimation  { target: pulseBox; property: "color";  from: "#ff8040"; to: "#ffe080"; duration: 250 }
            }
            ParallelAnimation {
                NumberAnimation { target: pulseBox; property: "width";  from: 180; to: 120; duration: 350; easing: "easeOutQuad" }
                NumberAnimation { target: pulseBox; property: "height"; from: 180; to: 120; duration: 350; easing: "easeOutQuad" }
                ColorAnimation  { target: pulseBox; property: "color";  from: "#ffe080"; to: "#ff8040"; duration: 350 }
            }
        }
    }

    Text {
        x: 80
        y: 320
        text: "Sequential { Parallel { ... } Parallel { ... } }"
        color: "#a0c0ff"
        fontSize: 14
    }

    Rectangle {
        id: toastBox
        x: 360
        y: 110
        width: 280
        height: 60
        color: "#5070ff"
        opacity: 0

        Text {
            x: 18
            y: 18
            text: "Toast: saved"
            color: "#ffffff"
            fontSize: 18
        }

        SequentialAnimation {
            id: toast
            OpacityAnimation { target: toastBox; from: 0; to: 1; duration: 320; easing: "easeOutQuad" }
            PauseAnimation { duration: 800 }
            OpacityAnimation { target: toastBox; from: 1; to: 0; duration: 320; easing: "easeOutQuad" }
            ScriptAction { onTrigger: compositeShowcase.cycles = compositeShowcase.cycles + 1 }
        }
    }

    Text {
        x: 360
        y: 320
        text: "Opacity in → PauseAnimation 800ms → out → ScriptAction"
        color: "#a0c0ff"
        fontSize: 14
    }

    Timer {
        interval: 2000
        repeat: true
        running: true
        onTriggered: { pulse.running = true; toast.running = true; }
    }
}
