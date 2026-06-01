import QtQuick

// Minimal stub used to probe TopAppBar in isolation. The real IconButton pulls
// in QtQuick.Effects + Ripple (a separate milestone).
Item {
    property string icon: ""
    property string type: "standard"
    property bool enabled: true
    signal clicked()
    implicitWidth: 48
    implicitHeight: 48
}
