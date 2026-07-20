package io.github.timer_err.qml4j.demo;

import io.github.timer_err.qml4j.engine.binding.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// A stand-in for Haedus's `client` ClientModel context object, so the ClickGui.qml can run in
// this demo host with no game client attached. Reactive fields are qml4j Properties (reads
// register a binding dependency, writes notify) so toggling a row's `expanded`/`enabled`
// actually drives the derived-height expand animation -- the layout path we're exercising.
public final class MockClient {

    public final String seed = "#8ab4f8";
    public final List<Object> panels = new ArrayList<>();

    // p/3 and p%3 are deliberate integer grid math (row/column of a 3-wide panel layout),
    // passed into Panel's double x/y -- the floor division is intended, not a lost fraction.
    @SuppressWarnings("IntegerDivisionInFloatingPointContext")
    public MockClient() {
        String[] titles = {"Combat", "Movement", "Render", "Player", "World", "Misc"};
        String[] mods = {"KillAura", "Velocity", "Fly", "Speed", "ESP", "Nametags",
            "AutoClicker", "Reach", "Sprint", "NoFall", "Scaffold", "FastPlace"};
        int mi = 0;
        for (int p = 0; p < titles.length; p++) {
            Panel panel = new Panel(titles[p], 8 + (p % 3) * 140, 8 + (p / 3) * 280);
            int rows = 4 + (p % 3);
            for (int r = 0; r < rows; r++) {
                Row row = new Row(mods[mi % mods.length] + (mi >= mods.length ? "2" : ""), (r % 3) != 0);
                mi++;
                // Give each toggleable module a handful of settings so expanding does real
                // derived-height layout work (a Column summing several ValueRows).
                if (row.hasSettings) {
                    row.values.add(Value.number("Range", 3.0, 1.0, 8.0, 0.1));
                    row.values.add(Value.number("Speed", 12.0, 0.0, 20.0, 0.5));
                    row.values.add(Value.bool("Through Walls", true));
                    row.values.add(Value.mode("Mode", "Switch", "Single", "Switch", "Multi"));
                    row.values.add(Value.bool("Visualize", false));
                    row.values.add(Value.color("Color", "#8ab4f8"));
                }
                panel.rows.add(row);
            }
            panels.add(panel);
        }
    }

    public static final class Panel {
        public final Property<Number> x0 = new Property<>(0);
        public final Property<Number> y0 = new Property<>(0);
        public final String title;
        public final List<Object> rows = new ArrayList<>();

        Panel(String title, double x, double y) {
            this.title = title;
            this.x0.set(x);
            this.y0.set(y);
        }

        public void savePos(double x, double y) {
            x0.set(x);
            y0.set(y);
        }
    }

    public static final class Row {
        public final String name;
        public final boolean toggleable;
        public final boolean hasSettings;
        public final Property<Boolean> enabled = new Property<>(Boolean.FALSE);
        public final Property<Boolean> expanded = new Property<>(Boolean.FALSE);
        public final List<Object> values = new ArrayList<>();

        Row(String name, boolean toggleable) {
            this.name = name;
            this.toggleable = toggleable;
            this.hasSettings = true;
        }

        public void toggle() {
            enabled.set(!Boolean.TRUE.equals(enabled.peek()));
        }

        public void toggleExpanded() {
            expanded.set(!Boolean.TRUE.equals(expanded.peek()));
        }
    }

    public static final class Value {
        public final String type;
        public final String name;
        public final Property<Boolean> visible = new Property<>(Boolean.TRUE);
        public final Property<Boolean> checked = new Property<>(Boolean.FALSE);
        public final Property<Number> num = new Property<>(0);
        public final double min;
        public final double max;
        public final double step;
        public final Property<String> mode = new Property<>("");
        public final List<Object> modes = new ArrayList<>();
        public final Property<String> color = new Property<>("#000000");

        private Value(String type, String name, double min, double max, double step) {
            this.type = type;
            this.name = name;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        static Value bool(String name, boolean on) {
            Value v = new Value("bool", name, 0, 1, 1);
            v.checked.set(on);
            return v;
        }

        static Value number(String name, double n, double min, double max, double step) {
            Value v = new Value("number", name, min, max, step);
            v.num.set(n);
            return v;
        }

        static Value mode(String name, String current, String... options) {
            Value v = new Value("mode", name, 0, 0, 0);
            v.mode.set(current);
            v.modes.addAll(Arrays.asList(options));
            return v;
        }

        static Value color(String name, String hex) {
            Value v = new Value("color", name, 0, 0, 0);
            v.color.set(hex);
            return v;
        }

        public void setOn(boolean on) {
            checked.set(on);
        }

        public void setNum(double n) {
            num.set(n);
        }

        public void setMode(String m) {
            mode.set(m);
        }
    }
}
