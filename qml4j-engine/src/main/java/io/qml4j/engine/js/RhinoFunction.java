package io.qml4j.engine.js;

import io.qml4j.engine.Callable;

import java.util.Arrays;

// A QML function (`function f(a, b) { ... }`) whose body is JavaScript run by Rhino.
// Registered on its QObject via __putFunction, so both bare calls (`f()`) and member
// calls (`root.f()`) reach it through RuntimeHelpers.callQml/callMethod. The body
// runs as a JS function (see RhinoClosure) and its `return` value is handed back to
// the caller.
public final class RhinoFunction implements Callable {

    private final RhinoClosure closure;

    public RhinoFunction(String body, String[] params, Object outer, Object root, String[] ids) {
        this.closure = new RhinoClosure(body, Arrays.asList(params), outer, root, ids);
    }

    @Override
    public Object call(Object[] args) {
        return closure.invoke(args);
    }
}
