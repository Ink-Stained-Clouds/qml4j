package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

// One-off probe (not a regression test): tries to instantiate every upstream MD3
// control from a local clone to categorize which load on the current engine and
// which hit a gap. Run with: -Dtest=Md3UpstreamProbe -Dmcq=/tmp/mcq
// Skipped when the clone is absent.
class Md3UpstreamProbe {

    private static final String[] HAVE = {
        "Button", "Card", "Checkbox", "Chip", "Dialog", "FAB", "IconButton",
        "NavigationBar", "RadioButton", "Ripple", "ScrollBar", "SegmentedButton",
        "Slider", "Snackbar", "Switch", "ToolTip", "TopAppBar"
    };

    @Test
    void probe() throws IOException {
        Path repo = Path.of(System.getProperty("mcq", "/tmp/mcq"));
        Path controls = repo.resolve("src/Core/Controls");
        Assumptions.assumeTrue(Files.isDirectory(controls), "MD3 clone not found at " + repo);

        Map<String, byte[]> files = new HashMap<>();
        List<String> names = new ArrayList<>();
        StringBuilder qmldir = new StringBuilder("singleton Theme 1.0 Theme.qml\n");
        // Use our ported (static) Theme singleton, not upstream's StyleManager-driven
        // one (M57, unimplemented) -- the goal is to probe the COMPONENTS, and ours
        // already provides the color roles they read.
        try (InputStream in = Md3UpstreamProbe.class.getResourceAsStream("/md3/Core/Theme.qml")) {
            files.put("md3/Core/Theme.qml", in.readAllBytes());
        }
        try (Stream<Path> s = Files.list(controls)) {
            for (Path p : (Iterable<Path>) s.sorted()::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(".qml")) continue;
                String name = fn.substring(0, fn.length() - 4);
                files.put("md3/Core/" + fn, deBom(Files.readAllBytes(p)));
                if (!name.equals("Theme")) {
                    qmldir.append(name).append(" 1.0 ").append(fn).append('\n');
                    names.add(name);
                }
            }
        }
        files.put("md3/Core/qmldir", qmldir.toString().getBytes(StandardCharsets.UTF_8));

        Map<String, String> results = new TreeMap<>();
        for (String name : names) {
            results.put(name, probeOne(files, name));
        }

        Map<String, List<String>> byBucket = new TreeMap<>();
        for (Map.Entry<String, String> e : results.entrySet()) {
            boolean have = List.of(HAVE).contains(e.getKey());
            String bucket = (e.getValue().equals("OK") ? "OK" : "FAIL") + (have ? " (ported)" : " (NEW)");
            byBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(e.getKey());
        }

        StringBuilder report = new StringBuilder("\n===== MD3 upstream probe =====\n");
        long ok = results.values().stream().filter("OK"::equals).count();
        report.append(ok).append('/').append(results.size()).append(" load\n\n");
        for (Map.Entry<String, List<String>> e : byBucket.entrySet()) {
            report.append('[').append(e.getKey()).append("]  ").append(e.getValue()).append('\n');
        }
        report.append("\n----- failures (first error) -----\n");
        for (Map.Entry<String, String> e : results.entrySet()) {
            if (!e.getValue().equals("OK")) {
                report.append(String.format("%-18s %s%n", e.getKey(), e.getValue()));
            }
        }
        System.out.println(report);
    }

    // Upstream files carry a UTF-8 BOM; our ports strip it. Strip here so the probe
    // measures real engine gaps, not the BOM byte.
    private static byte[] deBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            byte[] out = new byte[b.length - 3];
            System.arraycopy(b, 3, out, 0, out.length);
            return out;
        }
        return b;
    }

    private static String probeOne(Map<String, byte[]> files, String name) {
        try {
            QmlView v = QmlView.withStockTypes(new QmlEngine());
            v.resources(files::get);
            // Instantiate as a CHILD (factory path), not the document root: a
            // compound-root wrapper would subclass the component's FINAL generated
            // class and fail spuriously.
            Item root = v.load("import QtQuick\nimport md3.Core\nItem { " + name + " { } }\n");
            DirtyQueue dq = v.dirtyQueue();
            dq.install();
            try { dq.flush(); } finally { dq.uninstall(); }
            return root == null ? "FAIL: null root" : "OK";
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null) msg = t.getClass().getSimpleName();
            return t.getClass().getSimpleName() + ": " + msg.replaceAll("\\s+", " ").trim();
        }
    }
}
