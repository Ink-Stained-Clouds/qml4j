import QtQuick
import md3.Core

// Real MD3 SegmentedButton: single-select + multi-select. Tap a segment to toggle the
// checkmark -- _handleClicked rebuilds the buttons array with a for-in shallow copy
// (RhinoFunction), the Repeater delegate re-renders, and clicked(index) fires.
Rectangle {
    id: root
    x: 0
    y: 0
    color: Theme.color.surface

    property int lastClicked: -1

    Text {
        x: 16; y: 12
        text: "MD3 SegmentedButton (real component):"
        color: Theme.color.onSurfaceColor; fontSize: 24; width: parent.width - 32; height: 32
    }

    Column {
        x: 16; y: 56
        spacing: 24

        Text {
            text: "Single-select"
            color: Theme.color.onSurfaceVariantColor; fontSize: 14
        }
        SegmentedButton {
            width: 360
            buttons: [
                { text: "Day", icon: "calendar_today", selected: false },
                { text: "Week", icon: "date_range", selected: true },
                { text: "Month", icon: "calendar_month", selected: false }
            ]
            onClicked: (index) => root.lastClicked = index
        }

        Text {
            text: "Multi-select"
            color: Theme.color.onSurfaceVariantColor; fontSize: 14
        }
        SegmentedButton {
            width: 360
            multiSelect: true
            buttons: [
                { text: "Bold", icon: "format_bold", selected: true },
                { text: "Italic", icon: "format_italic", selected: false },
                { text: "Underline", icon: "format_underlined", selected: false }
            ]
        }
    }

    Text {
        x: 16; y: 260
        text: root.lastClicked < 0 ? "Tap a segment" : "Single-select last clicked: " + root.lastClicked
        color: Theme.color.onSurfaceColor; fontSize: 16
    }
}
