pragma Singleton
import QtQuick

QtObject {
    property QtObject color: QtObject {
        property color outline: "#79747e"
        property color primary: "#6750a4"
        property color inverseSurface: "#322f35"
        property color inverseOnSurface: "#f5eff7"
        property color surface: "#fef7ff"
        property color onSurfaceColor: "#1d1b20"
        property color onSurfaceVariantColor: "#49454f"
        property color onPrimaryColor: "#ffffff"
        property color surfaceContainerHighest: "#e6e0e9"
        property color shadow: "#000000"
    }
    property QtObject iconFont: QtObject {
        property string name: "Material Symbols Outlined"
    }
    property QtObject typography: QtObject {
        property QtObject bodySmall: QtObject {
            property string family: "Roboto"
            property int size: 12
            property int weight: 50
        }
        property QtObject labelLarge: QtObject {
            property string family: "Roboto"
            property int size: 14
            property int weight: 57
        }
        property QtObject titleLarge: QtObject {
            property string family: "Roboto"
            property int size: 22
            property int weight: 50
        }
    }
    property QtObject shape: QtObject {
        property int extraSmall: 4
        property int small: 8
    }
}
