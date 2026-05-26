package io.qml4j.android;

import android.app.Activity;
import android.os.Bundle;

import io.qml4j.engine.QmlEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {

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

        glView = new QmlGLSurfaceView(this, engine, qml);
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
