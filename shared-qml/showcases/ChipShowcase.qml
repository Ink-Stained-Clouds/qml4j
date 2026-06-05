import QtQuick
import md3.Core

// Real MD3 Chip, unmodified: assist / filter (selectable) / input (close icon) / suggestion.
// Tap a filter chip to toggle its check + container; tap the input chip's x to "close".
Rectangle {
    id: root
    x: 0
    y: 0
    color: "#fef7ff"

    Text {
        x: 16; y: 12
        text: "MD3 Chip (assist / filter / input / suggestion):"
        color: "#1d1b20"; fontSize: 24; width: parent.width - 32; height: 32
    }

    Column {
        x: 16; y: 56
        spacing: 14

        Row {
            spacing: 12
            Chip { type: "assist"; text: "Assist"; icon: "settings" }
            Chip { type: "suggestion"; text: "Suggestion" }
        }
        Row {
            spacing: 12
            Chip { type: "filter"; text: "Filter A"; selected: true }
            Chip { type: "filter"; text: "Filter B"; selected: false }
        }
        Row {
            spacing: 12
            Chip { type: "input"; text: "Input"; icon: "person" }
        }
    }
}
