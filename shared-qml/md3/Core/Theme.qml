pragma Singleton
import QtQuick

QtObject {
    property QtObject color: QtObject {
        property color outline: "#79747e"
        property color primary: "#6750a4"
        property color inverseSurface: "#322f35"
        property color inverseOnSurface: "#f5eff7"
        property color inversePrimary: "#d0bcff"
        property color surface: "#fef7ff"
        property color onSurfaceColor: "#1d1b20"
        property color onSurfaceVariantColor: "#49454f"
        property color onPrimaryColor: "#ffffff"
        property color primaryContainer: "#eaddff"
        property color onPrimaryContainerColor: "#21005d"
        property color surfaceContainerHighest: "#e6e0e9"
        property color surfaceContainerHigh: "#ece6f0"
        property color surfaceContainerLow: "#f7f2fa"
        property color secondary: "#625b71"
        property color surfaceVariant: "#e7e0ec"
        property color secondaryContainer: "#e8def8"
        property color onSecondaryContainerColor: "#1d192b"
        property color shadow: "#000000"
    }
    property QtObject elevation: QtObject {
        property real level0: 0
        property real level1: 1
        property real level2: 3
        property real level3: 6
        property real level4: 8
    }
    property QtObject state: QtObject {
        property real hoverStateLayerOpacity: 0.08
        property real pressedStateLayerOpacity: 0.12
        property real focusStateLayerOpacity: 0.12
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
        property QtObject headlineSmall: QtObject {
            property string family: "Roboto"
            property int size: 24
            property int weight: 50
        }
        property QtObject bodyMedium: QtObject {
            property string family: "Roboto"
            property int size: 14
            property int weight: 50
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
        property int cornerSmall: 8
        property int cornerMedium: 12
        property int cornerLarge: 16
        property int cornerExtraLarge: 28
        property int cornerFull: 999
    }
}
