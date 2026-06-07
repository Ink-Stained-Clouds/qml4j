import QtQuick
import md3.Core

// Real MD3 Canvas chart controls, unmodified: bar / line / pie. Each draws through
// QtQuick Canvas (getContext('2d') -> Skija) and reads its color roles from Theme.
// Light page to match the MD3 light theme.
Rectangle {
    id: root
    x: 0
    y: 0
    color: Theme.color.surface

    Text {
        x: 16; y: 12
        text: "MD3 Canvas charts (bar / line / pie):"
        color: Theme.color.onSurfaceColor; fontSize: 24
        width: parent.width - 32; height: 32
    }

    Column {
        x: 16; y: 64
        spacing: 24

        Row {
            spacing: 24

            CanvasBarChart {
                width: 380; height: 220
                values: [12, 6, 9, 3, 11, 7]
                labels: ["Jan", "Feb", "Mar", "Apr", "May", "Jun"]
            }

            CanvasLineChart {
                width: 380; height: 220
                values: [4, 8, 6, 10, 12, 9, 14]
                labels: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
            }
        }

        CanvasPieChart {
            width: 280; height: 240
            values: [30, 20, 15, 10, 25]
            labels: ["A", "B", "C", "D", "E"]
            innerRadiusRatio: 0.5
        }
    }
}
