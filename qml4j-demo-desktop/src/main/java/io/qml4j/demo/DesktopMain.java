package io.qml4j.demo;

import io.qml4j.engine.QmlEngine;
import io.qml4j.render.QmlView;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class DesktopMain {

    private static final int INITIAL_W = 640;
    private static final int INITIAL_H = 480;

    public static void main(String[] args) throws IOException {
        String qml = loadResource("/demo.qml");

        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("glfwInit failed");
        }
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        long window = GLFW.glfwCreateWindow(INITIAL_W, INITIAL_H, "qml4j demo", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            GLFW.glfwTerminate();
            throw new IllegalStateException("glfwCreateWindow failed");
        }
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);

        int[] fw = new int[1];
        int[] fh = new int[1];
        GLFW.glfwGetFramebufferSize(window, fw, fh);

        GlfwSurfaceBackend backend = new GlfwSurfaceBackend(window, fw[0], fh[0]);
        backend.init(fw[0], fh[0]);

        QmlEngine engine = new QmlEngine();
        QmlView view = QmlView.withStockTypes(engine);
        view.setClipboard(new AwtClipboard());
        view.load(qml);
        if (view.root() != null) {
            view.root().width.set(fw[0]);
            view.root().height.set(fh[0]);
        }

        GLFWFramebufferSizeCallback resizeCb = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long win, int w, int h) {
                if (w <= 0 || h <= 0) return;
                backend.resize(w, h);
                if (view.root() != null) {
                    view.root().width.set(w);
                    view.root().height.set(h);
                }
            }
        };
        GLFW.glfwSetFramebufferSizeCallback(window, resizeCb);

        while (!GLFW.glfwWindowShouldClose(window)) {
            view.renderFrame(backend);
            GLFW.glfwPollEvents();
        }

        view.dispose();
        backend.dispose();
        resizeCb.free();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFWErrorCallback cb = GLFW.glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream in = DesktopMain.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("resource not found: " + path);
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
