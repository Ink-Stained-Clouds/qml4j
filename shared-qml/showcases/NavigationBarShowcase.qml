import QtQuick
import md3.Core

// Real MD3 NavigationBar, unmodified: a StackLayout content area over a bottom bar
// of Repeater-driven items. The selected item grows a secondary-container pill via
// State.when + Transition; tapping an item switches both the pill and the page.
Rectangle {
    id: root
    x: 0
    y: 0
    color: Theme.color.surface

    NavigationBar {
        id: nav
        anchors.fill: parent
        currentIndex: 1
        model: [
            { icon: "home",     text: "Home" },
            { icon: "search",   text: "Search" },
            { icon: "favorite", text: "Favorites" },
            { icon: "settings", text: "Settings" }
        ]

        Rectangle { color: "#ffd9d9"; Text { anchors.centerIn: parent; text: "Home page";      fontSize: 28; color: "#3b1f1f" } }
        Rectangle { color: "#d9ffe1"; Text { anchors.centerIn: parent; text: "Search page";    fontSize: 28; color: "#143b1f" } }
        Rectangle { color: "#d9e4ff"; Text { anchors.centerIn: parent; text: "Favorites page"; fontSize: 28; color: "#1f2a3b" } }
        Rectangle { color: "#fff3d9"; Text { anchors.centerIn: parent; text: "Settings page";  fontSize: 28; color: "#3b321f" } }
    }
}
