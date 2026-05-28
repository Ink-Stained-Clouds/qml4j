Rectangle {
    id: root
    color: "#202028"

    property int taps: 0
    property alias badgeColor: badge.color
    property alias dotX: dot.x

    function fib(n) {
        if (n < 2) { return n; }
        return fib(n - 1) + fib(n - 2);
    }

    function sumTo(n) {
        var s = 0;
        for (var i = 1; i <= n; i = i + 1) { s = s + i; }
        return s;
    }

    function evenSum(n) {
        var s = 0;
        for (var i = 0; i < n; i = i + 1) {
            if (i % 2 != 0) { continue; }
            s = s + i;
        }
        return s;
    }

    function firstSquareOver(threshold) {
        var i = 0;
        for (;;) {
            if (i * i > threshold) { break; }
            i = i + 1;
        }
        return i;
    }

    function stars(n) {
        var s = "";
        var i = 0;
        while (i < n) {
            s = s + "*";
            i = i + 1;
        }
        return s;
    }

    function arraySum() {
        var xs = [3, 7, 11, 13, 17];
        var s = 0;
        for (var i = 0; i < xs.length; i = i + 1) { s = s + xs[i]; }
        return s;
    }

    function configBox() { return { w: 240, h: 60, label: "config bag" }; }

    function grid(rows, cols) {
        var out = [];
        for (var r = 0; r < rows; r = r + 1) {
            var row = [];
            for (var c = 0; c < cols; c = c + 1) { row[c] = r * cols + c; }
            out[r] = row;
        }
        return out;
    }

    function diagSum(n) {
        var g = grid(n, n);
        var s = 0;
        for (var i = 0; i < n; i = i + 1) { s = s + g[i][i]; }
        return s;
    }

    signal bumped()
    onBumped: {
        badgeColor = badgeColor === "#80ff80" ? "#ff8080" : "#80ff80";
        root.taps = root.taps + 1;
    }

    Rectangle {
        id: box
        x: 60
        y: 120
        width: 280
        height: 280
        color: "#ff5050"

        states: [
            State {
                name: "big"
                PropertyChanges {
                    target: box
                    width: 720
                    height: 720
                    color: "#5050ff"
                    x: 60
                    y: 120
                }
            }
        ]

        transitions: [
            Transition {
                NumberAnimation {
                    properties: "width,height"
                    duration: 450
                    easing: "easeOutQuad"
                }
            }
        ]

        MouseArea {
            anchors.fill: parent
            onClicked: box.state = box.state === "big" ? "" : "big"
        }

        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            y: 16
            text: "tap to toggle"
            color: "#ffffff"
            fontSize: 28
        }
    }

    Text {
        x: 60
        y: 900
        text: box.state === "big" ? "state: big" : "state: (none)"
        color: "#a0a0c0"
        fontSize: 32
    }

    Rectangle {
        id: badge
        x: 60
        y: 980
        width: 200
        height: 80
        color: "#80ff80"
        opacity: 0.6

        Text {
            x: 16
            y: 24
            text: "opacity 0.6"
            color: "#000000"
            fontSize: 24
        }
    }

    Text {
        x: 500
        y: 900
        text: "taps: " + root.taps
        color: "#a0a0c0"
        fontSize: 32
    }

    Rectangle {
        id: dot
        x: 400
        y: 980
        width: 80
        height: 80
        color: "#ffcc00"

        Behavior on x { NumberAnimation { duration: 350; easing: "easeOutQuad" } }

        MouseArea {
            anchors.fill: parent
            onClicked: {
                var atLeft = root.dotX === 400;
                if (atLeft) {
                    root.dotX = 800;
                    dot.color = "#ff80ff";
                } else {
                    root.dotX = 400;
                    dot.color = "#ffcc00";
                }
                root.bumped.emit();
            }
        }

        Text {
            anchors.centerIn: parent
            text: "tap me"
            color: "#000000"
            fontSize: 22
        }
    }

    Column {
        x: 500
        y: 60
        spacing: 8

        Text {
            text: "fib(12) = " + fib(12)
            color: "#ffffff"
            fontSize: 26
            width: 520
            height: 36
        }
        Text {
            text: "sum(1..100) = " + sumTo(100)
            color: "#ffffff"
            fontSize: 26
            width: 520
            height: 36
        }
        Text {
            text: "even sum < 20 = " + evenSum(20)
            color: "#ffffff"
            fontSize: 26
            width: 520
            height: 36
        }
        Text {
            text: "min n: n*n > 500 -> " + firstSquareOver(500)
            color: "#ffffff"
            fontSize: 26
            width: 520
            height: 36
        }
        Text {
            text: stars(16)
            color: "#ffd060"
            fontSize: 32
            width: 520
            height: 44
        }
        Text {
            text: "sum([3,7,11,13,17]) = " + arraySum()
            color: "#80c0ff"
            fontSize: 26
            width: 520
            height: 36
        }
        Text {
            text: configBox().label + " w=" + configBox()["w"]
            color: "#80c0ff"
            fontSize: 26
            width: 520
            height: 36
        }
        Text {
            text: "diag sum 5x5 = " + diagSum(5)
            color: "#80c0ff"
            fontSize: 26
            width: 520
            height: 36
        }
    }

    Column {
        x: 60
        y: 1140
        spacing: 8

        Text {
            text: "Repeater (int model):"
            color: "#a0a0c0"
            fontSize: 24
            width: 480
            height: 32
        }
        Repeater {
            model: 6
            Rectangle {
                width: 40 + index * 40
                height: 24
                color: index % 2 === 0 ? "#80c0ff" : "#ffc080"
            }
        }
        Text {
            text: "Repeater (list model):"
            color: "#a0a0c0"
            fontSize: 24
            width: 480
            height: 32
        }
        Repeater {
            model: ["alpha", "beta", "gamma", "delta"]
            Text {
                text: index + ". " + modelData
                color: "#ffffff"
                fontSize: 22
                width: 480
                height: 30
            }
        }
    }
}
