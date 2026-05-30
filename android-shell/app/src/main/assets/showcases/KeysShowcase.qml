Rectangle {
    id: keysShowcase
    x: 60
    y: 6880
    width: 880
    height: 320
    color: keysShowcase.activeFocus ? "#1d2733" : "#161b22"
    focus: true
    activeFocusOnTab: true

    property int lastKey: 0
    property string lastText: ""
    property int returns: 0
    property int arrows: 0
    property string log: "tap the key bar below — Tab leaves to the focus demo"

    Keys.onPressed: {
        keysShowcase.lastKey = event.key;
        keysShowcase.lastText = event.text;
        keysShowcase.log = "key=" + event.key + " text='" + event.text + "'";
        if (event.text !== "") event.accepted = true;
    }

    Keys.onReturnPressed: {
        keysShowcase.returns = keysShowcase.returns + 1;
        event.accepted = true;
    }

    Keys.onLeftPressed: { keysShowcase.arrows = keysShowcase.arrows + 1; event.accepted = true }
    Keys.onRightPressed: { keysShowcase.arrows = keysShowcase.arrows + 1; event.accepted = true }
    Keys.onUpPressed: { keysShowcase.arrows = keysShowcase.arrows + 1; event.accepted = true }
    Keys.onDownPressed: { keysShowcase.arrows = keysShowcase.arrows + 1; event.accepted = true }

    Text {
        x: 16
        y: 16
        text: "M46 Keys attached element + KeyEvent + accepted"
        color: "#7ad0ff"
        fontSize: 18
    }

    Text {
        x: 16
        y: 60
        text: keysShowcase.log
        color: "#ffffff"
        fontSize: 16
    }

    Text {
        x: 16
        y: 100
        text: "returnPressed count: " + keysShowcase.returns
        color: "#cccccc"
        fontSize: 16
    }

    Text {
        x: 16
        y: 130
        text: "arrow-key count: " + keysShowcase.arrows
        color: "#cccccc"
        fontSize: 16
    }

    Text {
        x: 16
        y: 170
        text: "last text fragment: '" + keysShowcase.lastText + "'"
        color: "#cccccc"
        fontSize: 16
    }
}
