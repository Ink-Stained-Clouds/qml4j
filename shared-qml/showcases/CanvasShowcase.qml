import QtQuick

// QtQuick Canvas: imperative 2D drawing via getContext('2d') -> Skija.
Rectangle {
    x: 0
    y: 0
    color: "#12121a"

    Text {
        x: 16; y: 12
        text: "Canvas 2D — paths, arcs, gradient, dashed stroke, text"
        color: "#ffffff"; fontSize: 20
        width: parent.width - 32; height: 28
    }

    Canvas {
        x: 16
        y: 56
        width: parent.width - 32
        height: parent.height - 80

        onPaint: {
            var ctx = getContext("2d")
            ctx.clearRect(0, 0, width, height)

            // Filled rounded bars.
            var colors = ["#5070ff", "#50b070", "#b05070", "#c0a040", "#7050b0"]
            for (var i = 0; i < colors.length; i++) {
                ctx.fillStyle = colors[i]
                var bh = 40 + i * 22
                ctx.fillRect(20 + i * 60, 220 - bh, 44, bh)
            }

            // Stroked circle.
            ctx.strokeStyle = "#80c0ff"
            ctx.lineWidth = 6
            ctx.beginPath()
            ctx.arc(420, 130, 70, 0, 2 * Math.PI)
            ctx.stroke()

            // Radial-gradient filled circle.
            var g = ctx.createRadialGradient(600, 130, 0, 600, 130, 70)
            g.addColorStop(0, "#ffd060")
            g.addColorStop(1, "#c04060")
            ctx.fillStyle = g
            ctx.beginPath()
            ctx.arc(600, 130, 70, 0, 2 * Math.PI)
            ctx.fill()

            // Dashed polyline.
            ctx.strokeStyle = "#a0ffa0"
            ctx.lineWidth = 3
            ctx.setLineDash([10, 6])
            ctx.beginPath()
            ctx.moveTo(20, 300)
            for (var x = 20; x <= 680; x += 40) {
                ctx.lineTo(x, 300 + Math.sin(x / 40) * 40)
            }
            ctx.stroke()
            ctx.setLineDash([])

            // Text.
            ctx.fillStyle = "#ffffff"
            ctx.font = "28px sans-serif"
            ctx.textAlign = "center"
            ctx.fillText("Hello Canvas", parent.width / 2 - 16, 400)
        }
    }
}
