package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.core.Item;

public final class AnchorLine {

    public enum Edge {
        LEFT, RIGHT, HORIZONTAL_CENTER,
        TOP, BOTTOM, VERTICAL_CENTER
    }

    public final Item source;
    public final Edge edge;

    public AnchorLine(Item source, Edge edge) {
        this.source = source;
        this.edge = edge;
    }
}
