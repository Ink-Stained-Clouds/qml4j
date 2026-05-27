package io.qml4j.android;

import android.app.Activity;
import android.os.Bundle;

import io.qml4j.engine.QmlEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {

    static {
        // Skija's auto-loader detects os.name=Linux, arch=aarch64 and looks
        // for the .so as a JAR resource at io/github/humbleui/skija/linux/arm64/.
        // We ship it via jniLibs/arm64-v8a/ instead, so bypass the auto-loader
        // and load via System.loadLibrary ourselves.
        System.setProperty("skija.staticLoad", "false");
        System.loadLibrary("skija");
        // Pre-warm Skija classes so any JNI FindClass / class-ref caching
        // happens with the app classloader visible on the stack.
        try {
            Class.forName("io.github.humbleui.skija.ImageInfo");
            Class.forName("io.github.humbleui.skija.ColorInfo");
            Class.forName("io.github.humbleui.skija.ColorSpace");
            Class.forName("io.github.humbleui.skija.Color4f");
            Class.forName("io.github.humbleui.skija.Image");
            Class.forName("io.github.humbleui.skija.Canvas");
            Class.forName("io.github.humbleui.skija.Paint");
            Class.forName("io.github.humbleui.skija.Font");
            Class.forName("io.github.humbleui.types.Rect");
            Class.forName("io.github.humbleui.types.IRect");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private QmlGLSurfaceView glView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String qml;
        try {
            qml = readAsset("demo.qml");
        } catch (IOException e) {
            throw new RuntimeException("failed to read demo.qml asset", e);
        }

        QmlEngine engine = new QmlEngine(
            new DexClassLoaderBackend(getClass().getClassLoader()));

        glView = new QmlGLSurfaceView(this, engine, qml,
            new AssetResourceLoader(getAssets()));
        setContentView(glView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
    }

    private String readAsset(String name) throws IOException {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
