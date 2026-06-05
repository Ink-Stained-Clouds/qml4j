package io.qml4j.demo;

import io.qml4j.render.QmlView;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.system.MemoryUtil;

public final class DesktopMain {

    private static final int INITIAL_W = 720;
    private static final int INITIAL_H = 720;

    private long window;
    private GlfwSurfaceBackend backend;
    private DesktopHost host;

    // glfwGetCursorPos reports window (screen) coordinates; the QML root is sized in
    // framebuffer pixels. On HiDPI those differ, so scale every pointer coordinate.
    private float scaleX = 1f;
    private float scaleY = 1f;
    private double cursorX;
    private double cursorY;

    public static void main(String[] args) {
        new DesktopMain().run(args.length > 0 ? args[0] : null);
    }

    private void run(String initial) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("glfwInit failed");
        }
        createWindow();

        int[] fw = new int[1];
        int[] fh = new int[1];
        GLFW.glfwGetFramebufferSize(window, fw, fh);

        backend = new GlfwSurfaceBackend(window, fw[0], fh[0]);
        backend.init(fw[0], fh[0]);
        updateScale(fw[0], fh[0]);

        host = new DesktopHost(new DesktopResourceLoader(), fw[0], fh[0]);
        host.start(initial);

        installCallbacks();

        while (!GLFW.glfwWindowShouldClose(window)) {
            host.renderFrame(backend);
            GLFW.glfwPollEvents();
        }

        shutdown();
    }

    private void createWindow() {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(INITIAL_W, INITIAL_H, "qml4j showcases", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            GLFW.glfwTerminate();
            throw new IllegalStateException("glfwCreateWindow failed");
        }
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
    }

    private void updateScale(int fbW, int fbH) {
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetWindowSize(window, ww, wh);
        scaleX = ww[0] > 0 ? (float) fbW / ww[0] : 1f;
        scaleY = wh[0] > 0 ? (float) fbH / wh[0] : 1f;
    }

    private void installCallbacks() {
        GLFW.glfwSetFramebufferSizeCallback(window, new GLFWFramebufferSizeCallback() {
            @Override public void invoke(long win, int w, int h) {
                if (w <= 0 || h <= 0) return;
                backend.resize(w, h);
                host.resize(w, h);
                updateScale(w, h);
            }
        });
        GLFW.glfwSetCursorPosCallback(window, new GLFWCursorPosCallback() {
            @Override public void invoke(long win, double x, double y) {
                cursorX = x * scaleX;
                cursorY = y * scaleY;
                host.pointerMove((float) cursorX, (float) cursorY);
            }
        });
        GLFW.glfwSetMouseButtonCallback(window, new GLFWMouseButtonCallback() {
            @Override public void invoke(long win, int button, int action, int mods) {
                if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
                if (action == GLFW.GLFW_PRESS) host.pointerDown((float) cursorX, (float) cursorY);
                else if (action == GLFW.GLFW_RELEASE) host.pointerUp((float) cursorX, (float) cursorY);
            }
        });
        GLFW.glfwSetKeyCallback(window, new GLFWKeyCallback() {
            @Override public void invoke(long win, int key, int scancode, int action, int mods) {
                if (action == GLFW.GLFW_RELEASE) {
                    dispatchKey(key, mods, false);
                } else {
                    dispatchKey(key, mods, true);
                }
            }
        });
        GLFW.glfwSetCharCallback(window, new GLFWCharCallback() {
            @Override public void invoke(long win, int codepoint) {
                host.text(new String(Character.toChars(codepoint)));
            }
        });
    }

    private void dispatchKey(int glfwKey, int mods, boolean down) {
        int code = mapKey(glfwKey, mods);
        if (code == 0) return;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        host.key(code, null, down, shift);
    }

    // Printable characters arrive via the char callback; this maps only the control
    // keys QmlView understands. 0 means "not a control key" -> ignored here.
    private static int mapKey(int key, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE: return QmlView.KEY_BACKSPACE;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER: return QmlView.KEY_ENTER;
            case GLFW.GLFW_KEY_LEFT: return QmlView.KEY_LEFT;
            case GLFW.GLFW_KEY_RIGHT: return QmlView.KEY_RIGHT;
            case GLFW.GLFW_KEY_UP: return QmlView.KEY_UP;
            case GLFW.GLFW_KEY_DOWN: return QmlView.KEY_DOWN;
            case GLFW.GLFW_KEY_HOME: return QmlView.KEY_HOME;
            case GLFW.GLFW_KEY_END: return QmlView.KEY_END;
            case GLFW.GLFW_KEY_ESCAPE: return QmlView.KEY_ESCAPE;
            case GLFW.GLFW_KEY_TAB:
                return (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? QmlView.KEY_BACKTAB : QmlView.KEY_TAB;
            default: return 0;
        }
    }

    private void shutdown() {
        host.dispose();
        backend.dispose();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFWErrorCallback cb = GLFW.glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
