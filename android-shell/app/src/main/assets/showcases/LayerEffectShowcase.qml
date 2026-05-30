Rectangle {
    id: layerShowcase
    x: 60
    y: 8200
    width: 880
    height: 460
    color: "#0d1016"

    Text {
        x: 16
        y: 16
        text: "M48 layer effects: DropShadow / Glow / ColorOverlay"
        color: "#7ad0ff"
        fontSize: 18
    }

    Rectangle {
        x: 40
        y: 80
        width: 160
        height: 110
        radius: 10
        color: "#e8e8ee"
        layer.enabled: true
        layer.effect: DropShadow {
            offsetX: 8
            offsetY: 10
            radius: 18
            color: "#cc000000"
        }
        Text { anchors.centerIn: parent; text: "DropShadow"; color: "#202028"; fontSize: 15 }
    }

    Rectangle {
        x: 260
        y: 80
        width: 160
        height: 110
        radius: 10
        color: "#1b2a22"
        layer.enabled: true
        layer.effect: Glow {
            radius: 24
            color: "#33ddaa"
        }
        Text { anchors.centerIn: parent; text: "Glow"; color: "#7fffcc"; fontSize: 15 }
    }

    Rectangle {
        x: 480
        y: 80
        width: 160
        height: 110
        radius: 10
        color: "#3050a0"
        layer.enabled: true
        layer.effect: ColorOverlay {
            color: "#cc8800"
        }
        Text { anchors.centerIn: parent; text: "ColorOverlay"; color: "#ffffff"; fontSize: 14 }
    }

    Text {
        x: 40
        y: 220
        text: "shadow casts down-right; glow halos the card; overlay tints the whole layer"
        color: "#8893a4"
        fontSize: 13
    }

    Rectangle {
        x: 40
        y: 270
        width: 600
        height: 120
        radius: 12
        color: "#161b22"

        Text {
            anchors.centerIn: parent
            text: "glowing text layer"
            color: "#ffd060"
            fontSize: 30
            layer.enabled: true
            layer.effect: Glow {
                radius: 16
                color: "#ffb000"
            }
        }
    }
}
