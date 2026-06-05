package io.qml4j.engine.js;

import io.qml4j.engine.SignalHandler;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;

// A QML signal handler whose body is JavaScript run by Rhino. Unlike a binding it
// tracks no dependencies -- it runs imperatively for side effects. Each invocation
// gets a fresh call scope (a NativeObject) parented to the QmlScope, so the signal
// parameters and the body's `var` locals live there and resolve ahead of the
// enclosing component's members and the shared globals.
public final class RhinoHandler implements SignalHandler {

    private final Script script;
    private final QmlScope qmlScope;
    private final String[] params;

    public RhinoHandler(String source, String[] params, Object outer, Object root) {
        this.script = JsRuntime.compile(source);
        this.qmlScope = new QmlScope(outer, root, JsRuntime.globals());
        this.params = params;
    }

    @Override
    public void invoke(Object[] args) {
        Context cx = JsRuntime.enter();
        try {
            NativeObject call = new NativeObject();
            call.setParentScope(qmlScope);
            call.setPrototype(ScriptableObject.getObjectPrototype(qmlScope));
            for (int i = 0; i < params.length; i++) {
                Object a = i < args.length ? args[i] : null;
                call.put(params[i], call, JsWrap.toJs(a, qmlScope));
            }
            script.exec(cx, call);
        } catch (EcmaError e) {
            // QML handler semantics: an evaluation error is non-fatal, matching the
            // binding backend's tolerance for null-member reads / undefined refs.
        } finally {
            Context.exit();
        }
    }
}
