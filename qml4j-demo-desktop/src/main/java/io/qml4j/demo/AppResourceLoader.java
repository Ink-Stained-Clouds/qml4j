package io.qml4j.demo;

import io.qml4j.render.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

// Resolves the upstream MD3 app (cloned at /tmp/mcq): md3.Core maps to our bundled
// (StyleManager-driven) Theme plus the full upstream Controls set behind a generated
// qmldir; everything else comes from the App source tree.
final class AppResourceLoader implements ResourceLoader {

    // The upstream clone; override with -Dqml4j.mcq=/path (defaults to the usual /tmp clone).
    static final Path MCQ = Path.of(System.getProperty("qml4j.mcq", "/tmp/mcq"));
    static final Path APP = MCQ.resolve("src/App");
    static final Path CORE = MCQ.resolve("src/Core");

    @Override
    public byte[] load(String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("md3/Core/qmldir")) {
            return qmldir().getBytes(StandardCharsets.UTF_8);
        }
        if (path.equals("md3/Core/Theme.qml")) {
            try (InputStream in = AppResourceLoader.class.getResourceAsStream("/md3/Core/Theme.qml")) {
                return in == null ? null : in.readAllBytes();
            } catch (Exception e) {
                return null;
            }
        }
        if (path.startsWith("md3/Core/")) {
            String rel = path.substring("md3/Core/".length());
            // The qmldir flattens Controls/*.qml to md3/Core/*; other subdirs (Styles/assets)
            // map straight under src/Core.
            byte[] direct = readFile(CORE.resolve(rel));
            return direct != null ? direct : readFile(CORE.resolve("Controls").resolve(rel));
        }
        // `import md3.App`: the App's own QML module. Only its singletons need declaring
        // (DesktopWidgetManager); plain types resolve via relative-dir imports. Files map
        // straight under src/App.
        if (path.equals("md3/App/qmldir")) {
            return "singleton DesktopWidgetManager 1.0 widgets/DesktopWidgetManager.qml\n"
                .getBytes(StandardCharsets.UTF_8);
        }
        if (path.startsWith("md3/App/")) {
            return readFile(APP.resolve(path.substring("md3/App/".length())));
        }
        byte[] fromApp = readFile(APP.resolve(path));
        // Fall back to the classpath for bundled resources (e.g. fonts/) the app expects.
        return fromApp != null ? fromApp : fromClasspath(path);
    }

    private static byte[] fromClasspath(String path) {
        try (InputStream in = AppResourceLoader.class.getResourceAsStream("/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private static String qmldir() {
        StringBuilder sb = new StringBuilder("singleton Theme 1.0 Theme.qml\n");
        try (Stream<Path> s = Files.list(CORE.resolve("Controls"))) {
            s.sorted().forEach(p -> {
                String fn = p.getFileName().toString();
                if (fn.endsWith(".qml")) {
                    sb.append(fn, 0, fn.length() - 4).append(" 1.0 ").append(fn).append('\n');
                }
            });
        } catch (Exception e) {
            return sb.toString();
        }
        return sb.toString();
    }

    private static byte[] readFile(Path p) {
        try {
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
