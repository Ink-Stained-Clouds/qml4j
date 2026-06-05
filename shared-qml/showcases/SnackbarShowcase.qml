import QtQuick
import md3.Core

// Real MD3 Snackbar, unmodified: inverse-surface pill, anchored bottom-center,
// fades in via NumberAnimation and auto-dismisses on a Timer. One variant has an
// UNDO action button (Button contentItem delegate), the other a close icon.
Rectangle {
    id: root
    x: 0
    y: 0
    color: Theme.color.surface

    Text {
        x: 16; y: 12
        text: "MD3 Snackbar (tap a button; auto-dismisses, UNDO or close):"
        color: Theme.color.onSurfaceColor; fontSize: 22; width: parent.width - 32; height: 32
    }

    Column {
        x: 16; y: 72; spacing: 16
        Button { text: "Delete item"; type: "filled"; onClicked: snackClose.open() }
        Button { text: "Archive message"; type: "filledTonal"; onClicked: snackAction.open() }
    }

    Snackbar { id: snackClose; text: "Item deleted" }
    Snackbar { id: snackAction; text: "Message archived"; actionText: "UNDO" }
}
