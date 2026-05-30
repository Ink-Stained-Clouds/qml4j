Rectangle {
    id: es6Showcase
    x: 60
    y: 5780
    width: 880
    height: 500
    color: "#101620"

    property int a: 3
    property int b: 4
    property var nums: [a, b, a + b, a * b]
    property var label: `${a} + ${b} = ${a + b}, product = ${a * b}`

    function totalOf(x, y, z, w) { return x + y + z + w; }
    function squareList() {
        let square = n => n * n;
        let head = [square(a), square(b)];
        let tail = [square(a + b)];
        return [...head, ...tail];
    }

    Text {
        x: 16; y: 16
        text: "M44 ES6: template literals, spread, arrow"
        color: "#80f0a0"
        fontSize: 18
    }

    Text {
        x: 16; y: 60
        text: es6Showcase.label
        color: "#ffffff"
        fontSize: 22
    }

    Text {
        x: 16; y: 110
        text: `sum via spread: ${es6Showcase.totalOf(...es6Showcase.nums)}`
        color: "#ffffff"
        fontSize: 20
    }

    Text {
        x: 16; y: 160
        text: `squares: [${es6Showcase.squareList()}]`
        color: "#ffd080"
        fontSize: 20
    }

    Text {
        x: 16; y: 210
        text: "tap to mutate a"
        color: "#a0a0a0"
        fontSize: 14
    }

    Rectangle {
        x: 16; y: 240
        width: 140; height: 50
        color: "#3050a0"
        Text { x: 18; y: 14; text: "a += 1"; color: "#ffffff"; fontSize: 16 }
        MouseArea {
            anchors.fill: parent
            onClicked: es6Showcase.a = es6Showcase.a + 1
        }
    }

    Rectangle {
        x: 168; y: 240
        width: 140; height: 50
        color: "#a03060"
        Text { x: 18; y: 14; text: "b += 1"; color: "#ffffff"; fontSize: 16 }
        MouseArea {
            anchors.fill: parent
            onClicked: es6Showcase.b = es6Showcase.b + 1
        }
    }

    Text {
        x: 16; y: 310
        text: `nums = [${es6Showcase.nums}]`
        color: "#a0d0ff"
        fontSize: 18
    }

    Text {
        x: 16; y: 350
        text: `tail3 (spread): [${[0, ...es6Showcase.nums, 99]}]`
        color: "#d0a0ff"
        fontSize: 18
    }
}
