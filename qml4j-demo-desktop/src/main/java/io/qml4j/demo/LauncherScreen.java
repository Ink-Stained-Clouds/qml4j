package io.qml4j.demo;

import java.util.List;

// The launcher is a read-only QML list; clicks are resolved geometrically in
// indexAt() rather than via a QML->Java callback (the engine has no context
// properties). The QML layout below and indexAt() must stay in lockstep.
final class LauncherScreen {

    private static final int LEFT = 24;
    private static final int TOP = 70;
    private static final int COL_W = 300;
    private static final int COL_GAP = 24;
    private static final int ROW_H = 36;
    private static final int ROW_GAP = 8;
    private static final int STEP = ROW_H + ROW_GAP;
    private static final int COL1_X = LEFT + COL_W + COL_GAP;

    private final List<Showcase> showcases;
    private final int perCol;

    LauncherScreen(List<Showcase> showcases) {
        this.showcases = showcases;
        this.perCol = (showcases.size() + 1) / 2;
    }

    String qml() {
        StringBuilder b = new StringBuilder();
        b.append("import QtQuick\n");
        b.append("Rectangle {\n");
        b.append("    x: 0; y: 0\n");
        b.append("    color: \"#1c1b1f\"\n");
        b.append("    Text { x: ").append(LEFT).append("; y: 28;")
         .append(" text: \"qml4j showcases  -  click to open, Esc to return\";")
         .append(" color: \"#e6e1e5\"; fontSize: 18 }\n");
        appendColumn(b, LEFT, 0, perCol);
        appendColumn(b, COL1_X, perCol, showcases.size());
        b.append("}\n");
        return b.toString();
    }

    private void appendColumn(StringBuilder b, int x, int from, int to) {
        b.append("    Column { x: ").append(x).append("; y: ").append(TOP)
         .append("; spacing: ").append(ROW_GAP).append("\n");
        for (int i = from; i < to; i++) {
            String label = (i + 1) + ". " + showcases.get(i).title;
            b.append("        Rectangle { width: ").append(COL_W).append("; height: ")
             .append(ROW_H).append("; radius: 8; color: \"#2b2930\"\n");
            b.append("            Text { x: 14; y: 9; text: \"").append(label)
             .append("\"; color: \"#e6e1e5\"; fontSize: 16 }\n");
            b.append("        }\n");
        }
        b.append("    }\n");
    }

    int indexAt(float x, float y) {
        int col;
        if (x >= LEFT && x <= LEFT + COL_W) col = 0;
        else if (x >= COL1_X && x <= COL1_X + COL_W) col = 1;
        else return -1;

        float localY = y - TOP;
        if (localY < 0) return -1;
        int row = (int) (localY / STEP);
        if (localY - row * STEP > ROW_H) return -1;
        if (row >= perCol) return -1;

        int idx = col * perCol + row;
        return idx < showcases.size() ? idx : -1;
    }
}
