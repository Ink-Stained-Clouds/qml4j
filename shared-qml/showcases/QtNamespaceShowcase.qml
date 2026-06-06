Rectangle {
    id: qtNamespaceShowcase
    x: 60
    y: 5260
    width: 880
    height: 480
    color: "#181820"

    property real hue: 0.0
    property int factor: 3
    property int multiplier: 10
    property int derived: factor * multiplier
    property int laterHits: 0

    function rebindDerived() {
        qtNamespaceShowcase.derived = Qt.binding(() => qtNamespaceShowcase.factor * qtNamespaceShowcase.multiplier);
    }
    function imperativeOverride() {
        qtNamespaceShowcase.derived = 999;
    }
    function defer() {
        Qt.callLater(() => { qtNamespaceShowcase.laterHits = qtNamespaceShowcase.laterHits + 1 });
    }

    Text {
        x: 16
        y: 12
        text: "M43 Qt namespace: Qt.rgba / Qt.hsla / Qt.binding / Qt.callLater"
        color: "#ffffff"
        fontSize: 16
    }

    Rectangle {
        x: 16
        y: 60
        width: 200
        height: 80
        color: Qt.rgba(1.0, 0.4, 0.2, 1.0)
        Text { x: 12; y: 16; text: "Qt.rgba"; color: "#000000"; fontSize: 16 }
    }

    Rectangle {
        x: 240
        y: 60
        width: 200
        height: 80
        color: Qt.hsla(qtNamespaceShowcase.hue, 0.8, 0.5, 1.0)
        Text { x: 12; y: 16; text: "Qt.hsla (bound)"; color: "#000000"; fontSize: 16 }
    }

    Text {
        x: 16
        y: 170
        text: "factor=" + qtNamespaceShowcase.factor + "  derived=" + qtNamespaceShowcase.derived
        color: "#cccccc"
        fontSize: 16
    }

    Text {
        x: 16
        y: 200
        text: "callLater hits: " + qtNamespaceShowcase.laterHits
        color: "#cccccc"
        fontSize: 16
    }

    Rectangle {
        x: 16
        y: 250
        width: 180
        height: 50
        color: "#3060d0"
        Text { x: 16; y: 14; text: "Bump factor"; color: "#ffffff"; fontSize: 16 }
        MouseArea {
            anchors.fill: parent
            onClicked: qtNamespaceShowcase.factor = qtNamespaceShowcase.factor + 1
        }
    }

    Rectangle {
        x: 210
        y: 250
        width: 180
        height: 50
        color: "#d04040"
        Text { x: 16; y: 14; text: "Override"; color: "#ffffff"; fontSize: 16 }
        MouseArea {
            anchors.fill: parent
            onClicked: qtNamespaceShowcase.imperativeOverride()
        }
    }

    Rectangle {
        x: 404
        y: 250
        width: 180
        height: 50
        color: "#40a060"
        Text { x: 16; y: 14; text: "Rebind"; color: "#ffffff"; fontSize: 16 }
        MouseArea {
            anchors.fill: parent
            onClicked: qtNamespaceShowcase.rebindDerived()
        }
    }

    Rectangle {
        x: 598
        y: 250
        width: 180
        height: 50
        color: "#a040d0"
        Text { x: 16; y: 14; text: "callLater"; color: "#ffffff"; fontSize: 16 }
        MouseArea {
            anchors.fill: parent
            onClicked: qtNamespaceShowcase.defer()
        }
    }

    Timer {
        interval: 16
        repeat: true
        running: true
        onTriggered: qtNamespaceShowcase.hue = qtNamespaceShowcase.hue + 0.0032
    }
}
