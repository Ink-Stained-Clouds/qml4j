package io.github.timer_err.qml4j.compat;

import io.github.timer_err.qml4j.render.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// Loads QML (and fonts) that the build copies from shared-qml onto the test
// classpath. Used by MD3 integration / visual E2E tests so they exercise the
// same files the desktop host runs -- never a hand-patched fork of Theme/charts.
public final class ClasspathResources implements ResourceLoader {

    private final Map<String, byte[]> cache = new HashMap<>();

    public static ClasspathResources md3Core(String... extraFiles) {
        ClasspathResources r = new ClasspathResources();
        r.put("md3/Core/qmldir");
        r.put("md3/Core/Theme.qml");
        for (String f : extraFiles) {
            String path = f.startsWith("md3/") ? f : "md3/Core/" + f;
            r.put(path);
        }
        return r;
    }

    public ClasspathResources put(String classpathPath) {
        cache.put(classpathPath, read(classpathPath));
        return this;
    }

    public ClasspathResources putBytes(String path, byte[] bytes) {
        cache.put(path, bytes);
        return this;
    }

    @Override
    public byte[] load(String source) {
        if (source == null) return null;
        String key = source.startsWith("/") ? source.substring(1) : source;
        byte[] hit = cache.get(key);
        if (hit != null) return hit;
        // Allow on-demand reads for chart helpers that pull sibling files.
        try {
            byte[] bytes = read(key);
            cache.put(key, bytes);
            return bytes;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] read(String path) {
        try (InputStream in = ClasspathResources.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: /" + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
