import QtQuick
import QtQuick.Layouts

// QtQuick.Layouts: RowLayout / ColumnLayout + Layout.* attached properties.
Rectangle {
    x: 60
    y: 10760
    width: 880
    height: 300
    color: "#1c1c28"

    Text {
        x: 16
        y: 12
        text: "QtQuick.Layouts (RowLayout / ColumnLayout):"
        color: "#ffffff"
        fontSize: 24
        width: 820
        height: 32
    }

    // RowLayout with spacing, per-child margins and a fillWidth spacer.
    RowLayout {
        x: 16
        y: 56
        width: 840
        spacing: 8

        Rectangle { implicitWidth: 80; implicitHeight: 56; radius: 8; color: "#5070ff" }
        Rectangle { implicitWidth: 80; implicitHeight: 56; radius: 8; color: "#50b070"
            Layout.leftMargin: 12 }
        // Grows to eat the remaining width.
        Rectangle { implicitHeight: 40; radius: 8; color: "#404060"; Layout.fillWidth: true
            Text { anchors.centerIn: parent; text: "fillWidth"; color: "#ffffff"; fontSize: 20 } }
        Rectangle { implicitWidth: 80; implicitHeight: 56; radius: 8; color: "#b05070" }
    }

    // ColumnLayout stacking three bars.
    ColumnLayout {
        x: 16
        y: 140
        spacing: 6

        Rectangle { implicitWidth: 200; implicitHeight: 36; radius: 6; color: "#5070ff" }
        Rectangle { implicitWidth: 280; implicitHeight: 36; radius: 6; color: "#50b070" }
        Rectangle { implicitWidth: 160; implicitHeight: 36; radius: 6; color: "#b05070"
            Layout.alignment: Qt.AlignHCenter }
    }
}
