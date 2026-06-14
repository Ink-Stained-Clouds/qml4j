package io.github.timer_err.qml4j.engine.js;

import io.github.timer_err.qml4j.engine.SignalHandler;

import java.util.Arrays;

// A QML signal handler whose body is JavaScript run by Rhino. The body runs as a JS
// function (see RhinoClosure) so signal parameters bind as function arguments and a
// top-level `return` works; the result is discarded. Tracks no dependencies -- it
// runs imperatively for side effects.
public final class RhinoHandler implements SignalHandler {

    private final RhinoClosure closure;

    @SuppressWarnings("unused")
    public RhinoHandler(String body, String[] params, Object outer, Object root,
                        String[] ids, boolean delegate,
                        String[] singletonNames, Class<?>[] singletonClasses, String[] aliasSpecs) {
        this.closure = new RhinoClosure(body, Arrays.asList(params), outer, root, ids, delegate,
                                        singletonNames, singletonClasses, aliasSpecs);
    }

    @Override
    public void invoke(Object[] args) {
        closure.invoke(args);
    }
}
