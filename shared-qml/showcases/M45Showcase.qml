import "theme"
import "widgets" as W

Rectangle {
    id: m45Showcase
    x: 60
    y: 6300
    width: 880
    height: 540
    color: Theme.surface

    property int bumps: 0

    Text {
        x: Theme.padding
        y: Theme.padding
        text: "M45 modules: pragma Singleton + qmldir + import alias"
        color: Theme.primary
        fontSize: 18
    }

    Text {
        x: Theme.padding
        y: 60
        text: `Theme.primary = ${Theme.primary}, padding = ${Theme.padding}`
        color: "#ffffff"
        fontSize: 16
    }

    Rectangle {
        x: Theme.padding
        y: 100
        width: 360
        height: 64
        radius: 8
        color: Theme.primary
        Text {
            anchors.centerIn: parent
            text: "shared singleton fill"
            color: "#202028"
            fontSize: 18
        }
    }

    Rectangle {
        x: 400
        y: 100
        width: 360
        height: 64
        radius: 8
        color: Theme.accent
        Text {
            anchors.centerIn: parent
            text: `bumps = ${m45Showcase.bumps}`
            color: "#102018"
            fontSize: 18
        }
        MouseArea {
            anchors.fill: parent
            onClicked: m45Showcase.bumps = m45Showcase.bumps + 1
        }
    }

    Text {
        x: Theme.padding
        y: 200
        text: "imported via alias  W.Pill  (widgets/qmldir → pills/Pill.qml):"
        color: "#a0d0ff"
        fontSize: 16
    }

    W.Pill {
        x: Theme.padding
        y: 240
        label: "alpha"
        tint: Theme.primary
    }

    W.Pill {
        x: 180
        y: 240
        label: "beta"
        tint: Theme.accent
    }

    W.Pill {
        x: 340
        y: 240
        label: `taps ${m45Showcase.bumps}`
        tint: "#7c5cff"
    }

    Text {
        x: Theme.padding
        y: 320
        text: "tap the green bar above; both Theme refs share state, pill label re-binds"
        color: "#808898"
        fontSize: 14
    }
}
