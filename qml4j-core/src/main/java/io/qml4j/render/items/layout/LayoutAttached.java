package io.qml4j.render.items.layout;

import io.qml4j.engine.binding.Property;

// The QtQuick.Layouts `Layout.*` attached properties. Carried by every Item so
// `Layout.fillWidth: true` / `Layout.leftMargin: 4` resolve via the normal
// grouped-binding path (Item has a public field named `Layout`). Read by the
// enclosing RowLayout/ColumnLayout; ignored otherwise. preferred/min/max default
// to -1 = "unset" (fall back to the child's implicit size).
public final class LayoutAttached {
    public final Property<Boolean> fillWidth = new Property<>(Boolean.FALSE);
    public final Property<Boolean> fillHeight = new Property<>(Boolean.FALSE);
    public final Property<Number> preferredWidth = new Property<>(-1);
    public final Property<Number> preferredHeight = new Property<>(-1);
    public final Property<Number> minimumWidth = new Property<>(-1);
    public final Property<Number> minimumHeight = new Property<>(-1);
    public final Property<Number> maximumWidth = new Property<>(-1);
    public final Property<Number> maximumHeight = new Property<>(-1);
    public final Property<Number> alignment = new Property<>(0);
    public final Property<Number> margins = new Property<>(0);
    public final Property<Number> leftMargin = new Property<>(0);
    public final Property<Number> rightMargin = new Property<>(0);
    public final Property<Number> topMargin = new Property<>(0);
    public final Property<Number> bottomMargin = new Property<>(0);
    public final Property<Number> row = new Property<>(-1);
    public final Property<Number> column = new Property<>(-1);
    public final Property<Number> rowSpan = new Property<>(1);
    public final Property<Number> columnSpan = new Property<>(1);
}
