import QtQuick
import md3.Core

// A sampler of the newly-supported MD3 components (Tabs / Breadcrumb / ComboBox /
// LinearProgress / CircularProgress) running unmodified from the library.
Rectangle {
    id: root
    x: 0
    y: 0
    color: Theme.color.surface

    Text {
        x: 16; y: 12
        text: "MD3 components: Tabs / Breadcrumb / ComboBox / Progress"
        color: Theme.color.onSurfaceColor; fontSize: 22
        width: parent.width - 32; height: 30
    }

    Column {
        x: 16; y: 60
        width: parent.width - 32
        spacing: 28

        Tabs {
            width: 420
            currentIndex: 1
            model: [ { text: "Overview" }, { text: "Specs" }, { text: "Reviews" } ]
        }

        Breadcrumb {
            model: [ "Home", "Library", "Data" ]
        }

        ComboBox {
            width: 260
            label: "Fruit"
            currentIndex: 0
            model: [ "Apple", "Banana", "Cherry", "Date" ]
        }

        Text { text: "LinearProgress"; color: Theme.color.onSurfaceVariantColor; fontSize: 14 }
        LinearProgress { width: 420; value: 0.4 }
        LinearProgress { width: 420; value: 0.65; wavy: true }
        LinearProgress { width: 420; indeterminate: true }

        Row {
            spacing: 32
            CircularProgress { width: 56; height: 56; value: 0.7 }
            CircularProgress { width: 56; height: 56; indeterminate: true }
        }
    }
}
