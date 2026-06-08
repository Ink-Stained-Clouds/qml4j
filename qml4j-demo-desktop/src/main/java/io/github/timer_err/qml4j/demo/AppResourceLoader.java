package io.github.timer_err.qml4j.demo;

import io.github.timer_err.qml4j.render.ResourceLoader;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

// Resolves the upstream MD3 app: md3.Core maps to our bundled (StyleManager-driven) Theme
// plus the full upstream Controls set behind a generated qmldir; everything else comes from
// the App source tree. The app source is read from the on-disk clone (-Dqml4j.mcq, default
// /tmp/mcq) when present, else from the classpath under /mcq/** -- so a fat jar that bundles
// src/App and src/Core runs the app with no external files.
final class AppResourceLoader implements ResourceLoader {

    // The upstream clone; override with -Dqml4j.mcq=/path (defaults to the usual /tmp clone).
    static final Path MCQ = Paths.get(System.getProperty("qml4j.mcq", "/tmp/mcq"));
    static final Path APP = MCQ.resolve("src/App");
    static final Path CORE = MCQ.resolve("src/Core");

    // Qt resource scheme used by Loader sources (qrc:/qt/qml/md3/...); strip it so the path
    // routes through the normal md3/Core | md3/App logic. A bundled Extras/* that has no
    // public source then simply resolves to null (the Loader shows nothing), as on Linux --
    // and crucially never reaches Path.resolve, where the ':' is an illegal char on Windows.
    private static final String QRC_PREFIX = "qrc:/qt/qml/";

    @Override
    public byte[] load(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith(QRC_PREFIX)) {
            path = path.substring(QRC_PREFIX.length());
        }
        if (path.equals("md3/Core/qmldir")) {
            return qmldir().getBytes(StandardCharsets.UTF_8);
        }
        if (path.equals("md3/Core/Theme.qml")) {
            return fromClasspath("md3/Core/Theme.qml");
        }
        if (path.startsWith("md3/Core/")) {
            String rel = path.substring("md3/Core/".length());
            // The qmldir flattens Controls/*.qml to md3/Core/*; other subdirs (Styles/assets)
            // map straight under src/Core.
            byte[] direct = core(rel);
            return direct != null ? direct : core("Controls/" + rel);
        }
        // `import md3.App`: the App's own QML module. Only its singletons need declaring
        // (DesktopWidgetManager); plain types resolve via relative-dir imports. Files map
        // straight under src/App.
        if (path.equals("md3/App/qmldir")) {
            return "singleton DesktopWidgetManager 1.0 widgets/DesktopWidgetManager.qml\n"
                .getBytes(StandardCharsets.UTF_8);
        }
        if (path.startsWith("md3/App/")) {
            return app(path.substring("md3/App/".length()));
        }
        byte[] fromApp = app(path);
        // Fall back to the classpath for bundled resources (e.g. fonts/) the app expects.
        return fromApp != null ? fromApp : fromClasspath(path);
    }

    private static byte[] core(String rel) {
        return read(CORE, rel, "mcq/Core/" + rel);
    }

    private static byte[] app(String rel) {
        return read(APP, rel, "mcq/App/" + rel);
    }

    // On-disk clone first (live editing via -Dqml4j.mcq), then the bundled classpath copy.
    private static byte[] read(Path base, String rel, String classpathPath) {
        byte[] f = readFile(base, rel);
        return f != null ? f : fromClasspath(classpathPath);
    }

    private static byte[] fromClasspath(String path) {
        try (InputStream in = AppResourceLoader.class.getResourceAsStream("/" + path)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String qmldir() {
        StringBuilder sb = new StringBuilder("singleton Theme 1.0 Theme.qml\n");
        Path controls = CORE.resolve("Controls");
        if (Files.isDirectory(controls)) {
            try (Stream<Path> s = Files.list(controls)) {
                s.sorted().forEach(p -> appendControl(sb, p.getFileName().toString()));
            } catch (Exception e) {
                return sb.toString();
            }
            return sb.toString();
        }
        // No on-disk clone: enumerate the bundled Controls from the build-generated index
        // (a jar entry can't be listed like a directory).
        try (InputStream in = AppResourceLoader.class.getResourceAsStream("/mcq/Core/Controls.index")) {
            if (in == null) {
                return sb.toString();
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    appendControl(sb, line.trim());
                }
            }
        } catch (Exception e) {
            return sb.toString();
        }
        return sb.toString();
    }

    private static void appendControl(StringBuilder sb, String fn) {
        if (fn.endsWith(".qml")) {
            sb.append(fn, 0, fn.length() - 4).append(" 1.0 ").append(fn).append('\n');
        }
    }

    // Resolve `rel` under `base` and read it. rel may still carry an illegal segment (a
    // stray scheme/':'), which Path.resolve rejects on Windows -- treat that as not-found.
    private static byte[] readFile(Path base, String rel) {
        try {
            Path p = base.resolve(rel);
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
