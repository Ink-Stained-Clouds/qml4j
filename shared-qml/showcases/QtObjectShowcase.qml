import "apptheme"

Rectangle {
    id: qtObjectShowcase
    x: 60
    y: 9720
    width: 880
    height: 360
    color: AppTheme.color.surface

    property int bump: 0

    Text {
        x: AppTheme.padding
        y: AppTheme.padding
        text: "M51' QtObject singleton theme + nested groups + multi-dot binding"
        color: AppTheme.color.onSurface
        fontSize: AppTheme.typography.titleSize
    }

    Text {
        x: AppTheme.padding
        y: 60
        text: "AppTheme.color.primary = " + AppTheme.color.primary
        color: AppTheme.color.onSurface
        fontSize: AppTheme.typography.bodySize
    }

    Rectangle {
        x: AppTheme.padding
        y: 100
        width: 360
        height: 64
        radius: 8
        color: AppTheme.color.primary
        Text {
            anchors.centerIn: parent
            text: "primary (nested QtObject group)"
            color: "#ffffff"
            fontSize: AppTheme.typography.bodySize
        }
    }

    Rectangle {
        x: 400
        y: 100
        width: 360
        height: 64
        radius: 8
        color: AppTheme.color.secondary
        Text {
            anchors.centerIn: parent
            text: "taps = " + qtObjectShowcase.bump
            color: "#101018"
            fontSize: AppTheme.typography.bodySize
        }
        MouseArea {
            anchors.fill: parent
            onClicked: qtObjectShowcase.bump = qtObjectShowcase.bump + 1
        }
    }

    Text {
        x: AppTheme.padding
        y: 190
        text: "tap the purple bar; both blocks read colors from the singleton QtObject theme"
        color: "#9098a4"
        fontSize: 13
    }
}
