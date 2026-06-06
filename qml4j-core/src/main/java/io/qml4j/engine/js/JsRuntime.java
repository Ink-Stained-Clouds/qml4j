package io.qml4j.engine.js;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import java.util.concurrent.ConcurrentHashMap;

// Shared Rhino plumbing for the embedded-JS backend: an interpreter-mode context
// factory (optimizationLevel=-1 is mandatory on Android -- no runtime bytecode gen),
// a compiled-script cache (each binding's source compiles once), and one shared,
// sealed standard-objects scope (Math, JSON, etc.) reached via the binding scope's
// parent chain.
public final class JsRuntime {

    private JsRuntime() {}

    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            return cx;
        }
    };

    private static final ConcurrentHashMap<String, Script> SCRIPTS = new ConcurrentHashMap<>();
    private static volatile Scriptable globals;

    public static Context enter() {
        return FACTORY.enterContext();
    }

    public static Script compile(String source) {
        return SCRIPTS.computeIfAbsent(source, s -> {
            Context cx = enter();
            try {
                return cx.compileString(s, "qml-binding", 1, null);
            } finally {
                Context.exit();
            }
        });
    }

    public static Scriptable globals() {
        Scriptable g = globals;
        if (g != null) return g;
        synchronized (JsRuntime.class) {
            if (globals == null) {
                Context cx = enter();
                try {
                    globals = QtGlobals.build(cx);
                } finally {
                    Context.exit();
                }
            }
            return globals;
        }
    }
}
