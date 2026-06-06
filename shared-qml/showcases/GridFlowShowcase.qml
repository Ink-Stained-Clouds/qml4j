import QtQuick
import QtQuick.Layouts

// QtQuick.Layouts GridLayout (cells, spans, fill) + QtQuick Flow (wrapping).
Rectangle {
    x: 0
    y: 0
    color: "#1c1c28"

    Text {
        x: 16
        y: 12
        text: "GridLayout — columnSpan / rowSpan / fillWidth"
        color: "#ffffff"
        fontSize: 22
        width: parent.width - 32
        height: 28
    }

    GridLayout {
        x: 16
        y: 52
        width: parent.width - 32
        columns: 3
        rowSpacing: 8
        columnSpacing: 8

        // A header straddling all three columns.
        Rectangle {
            Layout.columnSpan: 3
            Layout.fillWidth: true
            implicitHeight: 44
            radius: 10
            color: "#3a3a5c"
            Text { anchors.centerIn: parent; text: "columnSpan: 3"; color: "#ffffff"; fontSize: 18 }
        }

        // Three equal cells sharing the row via fillWidth.
        Rectangle { Layout.fillWidth: true; implicitHeight: 64; radius: 8; color: "#5070ff"
            Text { anchors.centerIn: parent; text: "1"; color: "#ffffff"; fontSize: 20 } }
        Rectangle { Layout.fillWidth: true; implicitHeight: 64; radius: 8; color: "#50b070"
            Text { anchors.centerIn: parent; text: "2"; color: "#ffffff"; fontSize: 20 } }
        Rectangle { Layout.fillWidth: true; implicitHeight: 64; radius: 8; color: "#b05070"
            Text { anchors.centerIn: parent; text: "3"; color: "#ffffff"; fontSize: 20 } }

        // A cell spanning two rows on the left; cells 4-7 auto-flow around it.
        Rectangle {
            Layout.rowSpan: 2
            Layout.fillWidth: true
            Layout.fillHeight: true
            implicitHeight: 64
            radius: 8
            color: "#c08040"
            Text { anchors.centerIn: parent; text: "rowSpan: 2"; color: "#ffffff"; fontSize: 16 }
        }
        Rectangle { Layout.fillWidth: true; implicitHeight: 64; radius: 8; color: "#7050b0"
            Text { anchors.centerIn: parent; text: "4"; color: "#ffffff"; fontSize: 20 } }
        Rectangle { Layout.fillWidth: true; implicitHeight: 64; radius: 8; color: "#40a0c0"
            Text { anchors.centerIn: parent; text: "5"; color: "#ffffff"; fontSize: 20 } }
        Rectangle { Layout.fillWidth: true; implicitHeight: 64; radius: 8; color: "#a0c040"
            Text { anchors.centerIn: parent; text: "6"; color: "#ffffff"; fontSize: 20 } }
        Rectangle { Layout.fillWidth: true; implicitHeight: 64; radius: 8; color: "#c04080"
            Text { anchors.centerIn: parent; text: "7"; color: "#ffffff"; fontSize: 20 } }
    }

    Text {
        x: 16
        y: 360
        text: "Flow — tags wrap to the next line within width"
        color: "#ffffff"
        fontSize: 22
        width: parent.width - 32
        height: 28
    }

    Flow {
        x: 16
        y: 400
        width: parent.width - 32
        spacing: 8

        Rectangle { width: 96;  height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "alpha"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 150; height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "bravo charlie"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 80;  height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "delta"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 130; height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "echo foxtrot"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 110; height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "golf hotel"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 90;  height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "india"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 160; height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "juliet kilo lima"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 100; height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "mike nov"; color: "#cfe2ff"; fontSize: 16 } }
        Rectangle { width: 120; height: 38; radius: 19; color: "#2b3a52"
            Text { anchors.centerIn: parent; text: "oscar papa"; color: "#cfe2ff"; fontSize: 16 } }
    }
}
