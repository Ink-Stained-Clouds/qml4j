package io.qml4j.demo;

// Distributable fat-jar entry point: runs the bundled MD3 app with this build's defaults --
// vsync off (uncapped loop), the FPS overlay on, and the offscreen Canvas cache on. Each is
// only set when the user hasn't passed the corresponding -D flag, so overrides still win. The
// app source is read from the classpath (/mcq/**) because no on-disk clone exists on the
// target machine, so the jar is self-contained and cross-platform (Windows + Linux natives).
public final class DistMain {

    public static void main(String[] args) {
        setIfUnset("qml4j.vsync", "false");
        setIfUnset("qml4j.fps", "true");
        setIfUnset("qml4j.canvasCache", "true");
        DesktopMain.main(new String[]{"app"});
    }

    private static void setIfUnset(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
