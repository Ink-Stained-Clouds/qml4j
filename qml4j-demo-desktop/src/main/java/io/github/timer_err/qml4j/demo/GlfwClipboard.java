package io.github.timer_err.qml4j.demo;

import io.github.timer_err.qml4j.render.Clipboard;
import org.lwjgl.glfw.GLFW;

// GLFW-backed clipboard, so the host never touches AWT: no non-daemon EDT to keep the JVM
// alive past main(), and no AWT/GLFW contention for the macOS first thread. GLFW clipboard
// calls must run on the main thread, which is where every QML input handler already runs
// (dispatched from the callbacks inside glfwPollEvents).
final class GlfwClipboard implements Clipboard {

    private final long window;

    GlfwClipboard(long window) {
        this.window = window;
    }

    @Override
    public String getText() {
        // null when the clipboard is empty or holds no text, matching the SPI contract.
        return GLFW.glfwGetClipboardString(window);
    }

    @Override
    public void setText(String text) {
        GLFW.glfwSetClipboardString(window, text == null ? "" : text);
    }
}
