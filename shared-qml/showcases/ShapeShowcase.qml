Rectangle {
    id: shapeShowcase
    x: 60
    y: 7700
    width: 880
    height: 440
    color: "#10141c"

    Text {
        x: 16
        y: 16
        text: "M47 Shape + ShapePath: lines, curves, arcs"
        color: "#7ad0ff"
        fontSize: 18
    }

    Shape {
        x: 16
        y: 60
        ShapePath {
            fillColor: "#ff8800"
            strokeColor: "#a83e00"
            strokeWidth: 3
            startX: 0; startY: 120
            PathLine { x: 70; y: 0 }
            PathLine { x: 140; y: 120 }
            PathLine { x: 0; y: 120 }
        }
    }

    Shape {
        x: 200
        y: 60
        ShapePath {
            fillColor: "transparent"
            strokeColor: "#33ddaa"
            strokeWidth: 8
            capStyle: "RoundCap"
            joinStyle: "RoundJoin"
            startX: 0; startY: 70
            PathLine { x: 45; y: 115 }
            PathLine { x: 130; y: 0 }
        }
    }

    Shape {
        x: 380
        y: 60
        ShapePath {
            fillColor: "#5a7cff"
            strokeColor: "#2840a0"
            strokeWidth: 2
            startX: 0; startY: 120
            PathCubic {
                x: 150; y: 120
                control1X: 50; control1Y: -40
                control2X: 100; control2Y: 200
            }
            PathLine { x: 0; y: 120 }
        }
    }

    Shape {
        x: 560
        y: 60
        ShapePath {
            fillColor: "#d0507a"
            strokeColor: "#802040"
            strokeWidth: 2
            startX: 70; startY: 70
            PathLine { x: 70; y: 0 }
            PathArc {
                x: 0; y: 70
                radiusX: 70; radiusY: 70
                useLargeArc: false
                direction: "Counterclockwise"
            }
            PathLine { x: 70; y: 70 }
        }
    }

    Text {
        x: 16
        y: 210
        text: "triangle (fill+stroke)   checkmark (round stroke)   bezier   pie (arc)"
        color: "#8893a4"
        fontSize: 14
    }

    Shape {
        x: 16
        y: 250
        ShapePath {
            fillColor: "transparent"
            strokeColor: "#ffaa44"
            strokeWidth: 4
            joinStyle: "MiterJoin"
            startX: 0; startY: 40
            PathQuad { x: 80; y: 40; controlX: 40; controlY: -30 }
            PathQuad { x: 160; y: 40; controlX: 120; controlY: 110 }
            PathQuad { x: 240; y: 40; controlX: 200; controlY: -30 }
        }
    }

    Text {
        x: 16
        y: 320
        text: "quadratic wave (PathQuad chain)"
        color: "#8893a4"
        fontSize: 14
    }
}
