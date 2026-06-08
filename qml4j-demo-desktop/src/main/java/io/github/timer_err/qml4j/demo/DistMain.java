package io.github.timer_err.qml4j.demo;

// Distributable fat-jar entry point. Applies this build's defaults (vsync off / uncapped
// loop, FPS overlay on, offscreen Canvas cache on -- each only when the user hasn't passed
// the corresponding -D flag) and then forwards to DesktopMain: a `<projectDir> <entry.qml>`
// runs that QML, while no args defaults to the bundled MD3 app (its source is on the
// classpath under /mcq/**, so the jar is self-contained and cross-platform).
public final class DistMain {

    public static void main(String[] args) {
        setIfUnset("qml4j.vsync", "false");
        setIfUnset("qml4j.fps", "true");
        setIfUnset("qml4j.canvasCache", "true");
        DesktopMain.main(args.length > 0 ? args : new String[]{"app"});
    }

    private static void setIfUnset(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
