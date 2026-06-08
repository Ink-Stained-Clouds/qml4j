package io.github.timer_err.qml4j.demo;

import io.github.timer_err.qml4j.render.ResourceLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

// Resolves `import md3.Core` (and showcase loads) off the classpath. The shared-qml
// tree is mounted as a Maven resource, so md3/Core/X.qml lands at /md3/Core/X.qml.
public final class DesktopResourceLoader implements ResourceLoader {

    @Override
    public byte[] load(String source) {
        if (source == null) return null;
        String path = source.startsWith("/") ? source : "/" + source;
        try (InputStream in = DesktopResourceLoader.class.getResourceAsStream(path)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
