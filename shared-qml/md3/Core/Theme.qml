pragma Singleton
import QtQuick

QtObject {
    // MD3 dynamic color: the active (light/dark) scheme from the StyleManager singleton,
    // a role -> hex map. Theme.color.primary etc. read map keys.
    property var color: StyleManager.currentScheme
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
        property QtObject labelMedium: QtObject {
            property string family: "Roboto"
            property int size: 12
            property int weight: 57
        }
        property QtObject labelSmall: QtObject {
            property string family: "Roboto"
            property int size: 11
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
