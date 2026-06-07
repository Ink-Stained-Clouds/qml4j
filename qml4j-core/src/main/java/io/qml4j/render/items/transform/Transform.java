package io.qml4j.render.items.transform;

import io.qml4j.engine.QObject;

// Base for QtQuick Item.transform entries (Translate/Rotation/Scale). A list of these
// is applied, in order, by the Renderer before the item paints.
public abstract class Transform extends QObject {
}
