Rectangle {
    id: controlsShowcase
    x: 60
    y: 9240
    width: 880
    height: 420
    color: "#11151c"

    property int taps: 0

    Label {
        x: 16
        y: 16
        text: "M50 Controls: Label / Button / TextField"
        color: "#7ad0ff"
        fontSize: 18
    }

    Label {
        x: 16
        y: 56
        text: "Label is a Text with control defaults"
        color: "#aab4c0"
        fontSize: 14
    }

    Button {
        x: 16
        y: 96
        width: 160
        height: 48
        text: "Tap me"
        onClicked: controlsShowcase.taps = controlsShowcase.taps + 1
    }

    Button {
        x: 196
        y: 96
        width: 160
        height: 48
        text: "Disabled"
        enabled: false
        color: "#5a6472"
    }

    Label {
        x: 376
        y: 110
        text: "taps: " + controlsShowcase.taps
        color: "#ffffff"
        fontSize: 18
    }

    Label {
        x: 16
        y: 168
        text: "TextField (tap to focus, type):"
        color: "#aab4c0"
        fontSize: 14
    }

    TextField {
        id: nameField
        x: 16
        y: 200
        width: 300
        height: 44
        placeholderText: "your name"
        fontSize: 18
    }

    Label {
        x: 336
        y: 212
        text: nameField.text === "" ? "(empty)" : "hello, " + nameField.text
        color: "#33ddaa"
        fontSize: 18
    }
}
