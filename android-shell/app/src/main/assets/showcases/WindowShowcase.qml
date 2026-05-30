Rectangle {
    id: windowShowcase
    x: 60
    y: 8720
    width: 880
    height: 460
    color: "#0d1016"

    Text {
        x: 16
        y: 16
        text: "M49 Window + ApplicationWindow (embedded preview)"
        color: "#7ad0ff"
        fontSize: 18
    }

    Text {
        x: 16
        y: 50
        text: "Window: title + background color + content"
        color: "#8893a4"
        fontSize: 13
    }

    Window {
        x: 24
        y: 76
        width: 360
        height: 150
        color: "#243044"
        title: "Plain Window"
        Text { x: 16; y: 14; text: "Window content"; color: "#cfe0ff"; fontSize: 16 }
        Rectangle { x: 16; y: 50; width: 120; height: 70; radius: 8; color: "#ff8800" }
        Rectangle { x: 150; y: 50; width: 120; height: 70; radius: 8; color: "#33ddaa" }
    }

    Text {
        x: 16
        y: 244
        text: "ApplicationWindow: header (top) + footer (bottom) + content"
        color: "#8893a4"
        fontSize: 13
    }

    ApplicationWindow {
        x: 24
        y: 270
        width: 520
        height: 170
        color: "#1a2028"

        header: Rectangle {
            height: 36
            color: "#3050a0"
            Text { anchors.centerIn: parent; text: "Header"; color: "#ffffff"; fontSize: 15 }
        }

        footer: Rectangle {
            height: 28
            color: "#202830"
            Text { anchors.centerIn: parent; text: "Footer"; color: "#8893a4"; fontSize: 13 }
        }

        Text { x: 16; y: 60; text: "content area between header and footer"; color: "#cfe0ff"; fontSize: 15 }
    }
}
