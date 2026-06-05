import QtQuick
import md3.Core

// Real MD3 Slider, unmodified: continuous / stepped+ticks / range / value-label.
// Drag a handle -- the active track fills and the thumb reshapes (State.when),
// hit math via mapFromItem. Light page to match the MD3 light theme.
Rectangle {
    id: root
    x: 0
    y: 0
    color: Theme.color.surface

    Text {
        x: 16; y: 12
        text: "MD3 Slider (continuous / stepped / range / value label):"
        color: Theme.color.onSurfaceColor; fontSize: 24; width: parent.width - 32; height: 32
    }

    Column {
        x: 16; y: 72
        spacing: 36

        Slider { width: 360; from: 0; to: 100; value: 40 }
        Slider { width: 360; from: 0; to: 10; stepSize: 1; value: 3; snapMode: true }
        Slider { width: 360; rangeMode: true; from: 0; to: 100; firstValue: 25; secondValue: 75 }
        Slider { width: 360; from: 0; to: 1; value: 0.5; valueLabelEnabled: true }
    }
}
