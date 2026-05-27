Rectangle {
    color: "#202028"
    Rectangle {
        anchors.fill: parent
        anchors.margins: 40
        color: "#ff5050"
        MouseArea {
            anchors.fill: parent
            onClicked: parent.color = "#50ff50"
        }
        Image {
            x: 24
            y: 24
            width: 192
            height: 192
            source: "test.png"
        }
        Text {
            x: 24
            y: 240
            text: "tap the box"
            color: "#ffffff"
            fontSize: 28
        }
    }
}
