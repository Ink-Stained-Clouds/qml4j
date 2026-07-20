package io.github.timer_err.qml4j.engine.js;

import io.github.timer_err.qml4j.engine.classloader.ScriptCache;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Shared Rhino plumbing for the embedded-JS backend: a compiled-script cache (each
// binding's source compiles once), per-document when the document's class loader
// carries a ScriptCache (so its generated JS classes die with it), and one shared,
// sealed standard-objects scope (Math, JSON, etc.) reached via the binding scope's
// parent chain.
// Rhino's Context is entered/exited manually (Context.enter()/exit()), not via
// try-with-resources; the optimization-level knobs are deprecated in Rhino 1.9 but are
// still the supported way to force interpreted mode for our JIT-compiled bindings.
@SuppressWarnings({"deprecation", "AutoCloseableResource"})
public final class JsRuntime {

    private JsRuntime() {}

    // Compiled (JS -> JVM bytecode) by default: bindings/handlers run far faster than
    // interpreted. Android must force the interpreter (no runtime bytecode gen) with
    // -Dqml4j.rhino.opt=-1. First step of the compiled-mode / hot-reload direction;
    // a later step defines the generated classes into each component's classloader.
    private static final int OPT_LEVEL = Integer.getInteger("qml4j.rhino.opt", 9);

    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(OPT_LEVEL);
            cx.setLanguageVersion(Context.VERSION_ES6);
            return cx;
        }
    };

    private static final ConcurrentHashMap<String, Script> SCRIPTS = new ConcurrentHashMap<>();
    private static volatile Scriptable globals;

    public static Context enter() {
        return FACTORY.enterContext();
    }

    @SuppressWarnings("unused")
    public static Script compile(String source) {
        return compile(source, null);
    }

    // Compile `source`, caching on the document's class loader when it carries a
    // ScriptCache (so the compiled-JS class is a child of that loader and is freed
    // with the document), else in the shared global cache. The loader is also set as
    // the parent for Rhino's generated class so the JS can see the document's classes.
    public static Script compile(String source, ClassLoader cl) {
        Map<String, Object> cache = cl instanceof ScriptCache ? ((ScriptCache) cl).jsScriptCache() : null;
        if (cache != null) {
            Script cached = (Script) cache.get(source);
            if (cached != null) return cached;
            Script s = doCompile(source, cl);
            cache.put(source, s);
            return s;
        }
        return SCRIPTS.computeIfAbsent(source, s -> doCompile(s, cl));
    }

    // The document's class loader for a binding/handler: prefer the root component's
    // (a ScriptCache-carrying DynamicClassLoader), else the binding owner's.
    public static ClassLoader loaderOf(Object root, Object outer) {
        ClassLoader cl = root != null ? root.getClass().getClassLoader() : null;
        if (cl instanceof ScriptCache) return cl;
        return outer != null ? outer.getClass().getClassLoader() : cl;
    }

    private static Script doCompile(String source, ClassLoader cl) {
        Context cx = enter();
        try {
            if (cl != null) cx.setApplicationClassLoader(cl);
            return cx.compileString(JsConstRepair.toLet(source), "qml-binding", 1, null);
        } finally {
            Context.exit();
        }
    }

    // Syntax-validate `source` without generating a class: compiled mode would emit
    // (and leak) a throwaway class per source, so validation runs interpreted. Throws
    // RhinoException on a syntax error.
    public static void validate(String source) {
        Context cx = enter();
        int prev = cx.getOptimizationLevel();
        try {
            cx.setOptimizationLevel(-1);
            cx.compileString(JsConstRepair.toLet(source), "qml-validate", 1, null);
        } finally {
            cx.setOptimizationLevel(prev);
            Context.exit();
        }
    }

    // Install a host context property into the shared globals scope, where a binding's
    // QmlScope reaches it via the parent chain after its own lookups miss.
    public static void putGlobal(String name, Object value) {
        Scriptable g = globals();
        enter();
        try {
            g.put(name, g, JsWrap.toJs(value, g));
        } finally {
            Context.exit();
        }
    }

    // Evaluate a `.js` library imported as a namespace (`import "x.js" as Foo`) and return
    // its scope, whose top-level `var`s are the module's members (`Foo.icons`). The scope's
    // prototype is the shared globals so the module sees Math/JSON/etc.
    public static Scriptable evalModule(String source) {
        Context cx = enter();
        try {
            Scriptable scope = cx.newObject(globals());
            scope.setPrototype(globals());
            scope.setParentScope(null);
            cx.evaluateString(scope, JsConstRepair.toLet(source), "qml-js-module", 1, null);
            return scope;
        } finally {
            Context.exit();
        }
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
