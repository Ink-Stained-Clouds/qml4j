import QtQuick
import QtQuick.Effects
import md3.Core
MouseArea {
    id: root

    property color rippleColor: Theme.color.onSurfaceColor
    property real rippleOpacity: 0.12
    property real clipRadius: 0
    property alias clipTopLeftRadius: maskRect.topLeftRadius
    property alias clipTopRightRadius: maskRect.topRightRadius
    property alias clipBottomLeftRadius: maskRect.bottomLeftRadius
    property alias clipBottomRightRadius: maskRect.bottomRightRadius

    hoverEnabled: true

    // Mask for clipping (defines the shape)
    Item {
        id: mask
        anchors.fill: parent
        layer.enabled: true
        visible: false

        Rectangle {
            id: maskRect
            anchors.fill: parent
            radius: root.clipRadius
            color: "black"
        }
    }

    // Container that holds the live ripple waves (masked to the shape above).
    Item {
        id: rippleContent
        anchors.fill: parent
        visible: false
    }

    MultiEffect {
        source: rippleContent
        anchors.fill: parent
        maskEnabled: true
        maskSource: mask
    }

    // qml4j divergence from upstream md3 Ripple.qml: upstream reuses a single
    // ripple rectangle, so a new tap restarts the one wave. Material allows
    // overlapping ripples -- each touch is its own wave. We spawn an independent
    // wave per press (Component.createObject) that expands, fades, and destroys
    // itself, so concurrent taps coexist without disturbing each other.
    Component {
        id: waveComponent
        Rectangle {
            id: wave
            property real startX: 0
            property real startY: 0
            property real targetSize: Math.max(root.width, root.height) * 2.5
            property real size: 0

            width: size
            height: size
            radius: size / 2
            x: startX - size / 2
            y: startY - size / 2
            color: root.rippleColor
            opacity: 0

            NumberAnimation {
                target: wave; property: "size"
                from: 0; to: wave.targetSize
                duration: 450; easing.type: Easing.OutQuart
                running: true
            }
            SequentialAnimation {
                running: true
                NumberAnimation { target: wave; property: "opacity"; from: 0; to: root.rippleOpacity; duration: 90 }
                NumberAnimation { target: wave; property: "opacity"; to: 0; duration: 360; easing.type: Easing.InQuad }
            }
            // Self-destruct just after the longest leg (size 450ms / fade 450ms).
            Timer { interval: 470; running: true; repeat: false; onTriggered: wave.destroy() }
        }
    }

    onPressed: (mouse) => {
        waveComponent.createObject(rippleContent, { startX: mouse.x, startY: mouse.y })
    }
}
