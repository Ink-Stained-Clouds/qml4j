import QtQuick
import md3.Core

// Real MD3 Dialog, unmodified: scrim + scale/opacity enter-exit animation,
// reparented to the page root, dismissed by the scrim or the action buttons.
// Built on Component.createObject-free reparenting + animation finished signal.
Rectangle {
    id: root
    x: 0
    y: 0
    color: "#fef7ff"

    Text {
        x: 16; y: 12
        text: "MD3 Dialog (tap to open; scrim or a button dismisses):"
        color: "#1d1b20"; fontSize: 24; width: parent.width - 32; height: 32
    }

    Button {
        x: 16; y: 64
        type: "filled"
        text: "Show dialog"
        onClicked: dlg.open()
    }

    Dialog {
        id: dlg
        icon: "delete"
        title: "Delete file?"
        text: "This will permanently remove the file.  This action cannot be undone. This action cannot be undone. This action cannot be undone. This action cannot be undone."
        acceptText: "Delete"
        rejectText: "Cancel"
    }
}
