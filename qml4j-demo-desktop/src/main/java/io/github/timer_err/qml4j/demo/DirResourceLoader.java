package io.github.timer_err.qml4j.demo;

import io.github.timer_err.qml4j.render.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Resolves QML (and fonts, sub-imports like md3/Core/*) from a project directory on
// disk: the runner is pointed at a folder and loads everything relative to it, so
// nothing is bundled into the jar. `import md3.Core` -> <root>/md3/Core/*.qml, etc.
final class DirResourceLoader implements ResourceLoader {

    private final Path root;

    DirResourceLoader(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public byte[] load(String source) {
        if (source == null) return null;
        String rel = source.startsWith("/") ? source.substring(1) : source;
        try {
            return Files.readAllBytes(root.resolve(rel).normalize());
        } catch (IOException e) {
            return null;
        }
    }
}
