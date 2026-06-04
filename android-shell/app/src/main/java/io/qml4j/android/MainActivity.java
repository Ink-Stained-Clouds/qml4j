package io.qml4j.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.QmlView;

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
        // CRITICAL: Skija caches all jclass/jmethodID/jfieldID in native
        // _nAfterLoad(). Library.load() normally calls it, but we bypass that
        // with our own System.loadLibrary, so call it explicitly. Without this,
        // every native method that constructs/reads a Java object (measureText
        // -> Rect, getMetrics -> FontMetrics, Shaper) dereferences a NULL ID and
        // crashes natively; only primitive-returning calls work.
        io.github.humbleui.skija.impl.Library._nAfterLoad();
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams glLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        glView.setLayoutParams(glLp);
        root.addView(glView);
        root.addView(buildKeyBar());
        setContentView(root);
    }

    private LinearLayout buildKeyBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        addKeyButton(bar, "A", 65, "a");
        addKeyButton(bar, "Spc", 32, " ");
        addKeyButton(bar, "Ent", QmlView.KEY_ENTER, null);
        addKeyButton(bar, "Esc", QmlView.KEY_ESCAPE, null);
        addKeyButton(bar, "<", QmlView.KEY_LEFT, null);
        addKeyButton(bar, ">", QmlView.KEY_RIGHT, null);
        addKeyButton(bar, "Up", QmlView.KEY_UP, null);
        addKeyButton(bar, "Dn", QmlView.KEY_DOWN, null);
        addKeyButton(bar, "Tab", QmlView.KEY_TAB, null);
        addKeyButton(bar, "BTab", QmlView.KEY_BACKTAB, null);
        return bar;
    }

    private void addKeyButton(LinearLayout bar, String label, final int code, final String text) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMaxLines(1);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        b.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { glView.sendSyntheticKey(code, text); }
        });
        bar.addView(b);
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
