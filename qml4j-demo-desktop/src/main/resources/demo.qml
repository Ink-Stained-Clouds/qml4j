Rectangle {
    color: "#202028"

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

    Column {
        x: 24
        y: 24
        spacing: 6

        Text {
            text: "fib(12) = " + fib(12)
            color: "#ffffff"
            fontSize: 18
            width: 320
            height: 24
        }
        Text {
            text: "sum(1..100) = " + sumTo(100)
            color: "#ffffff"
            fontSize: 18
            width: 320
            height: 24
        }
        Text {
            text: "even sum < 20 = " + evenSum(20)
            color: "#ffffff"
            fontSize: 18
            width: 320
            height: 24
        }
        Text {
            text: "min n where n*n > 500 -> " + firstSquareOver(500)
            color: "#ffffff"
            fontSize: 18
            width: 320
            height: 24
        }
        Text {
            text: stars(16)
            color: "#ffd060"
            fontSize: 24
            width: 320
            height: 32
        }
        Rectangle {
            width: fib(10) * 4
            height: 24
            color: "#ff5050"
        }
        Rectangle {
            width: sumTo(8) * 6
            height: 24
            color: "#50a0ff"
        }
        Rectangle {
            width: firstSquareOver(200) * 20
            height: 24
            color: "#60d080"
        }
    }
}
