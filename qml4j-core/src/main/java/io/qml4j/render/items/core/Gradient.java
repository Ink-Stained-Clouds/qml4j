package io.qml4j.render.items.core;

import io.qml4j.engine.QObject;
import io.qml4j.engine.QmlDefaultList;

import java.util.ArrayList;
import java.util.List;

@QmlDefaultList("stops")
public class Gradient extends QObject {
    public final List<GradientStop> stops = new ArrayList<>();
}
