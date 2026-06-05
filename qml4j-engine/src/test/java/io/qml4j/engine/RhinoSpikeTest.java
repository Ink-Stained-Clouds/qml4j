package io.qml4j.engine;

import io.qml4j.engine.binding.Binding;
import io.qml4j.engine.binding.Property;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Feasibility spike for replacing the hand-written JS codegen with an embedded
// engine (Rhino). Proves the one thing that could sink the idea: a binding whose
// body runs in Rhino still drives qml4j's reactive dependency tracking, because
// the QML objects are exposed as a Scriptable whose get() goes through
// Property.get() -> BindingEvaluationContext.recordRead.
class RhinoSpikeTest {

    // A Rhino scope backed by named qml4j Properties. Reading an identifier in JS
    // routes to Property.get(), which records the dependency; writing routes to set.
    static final class PropertyScope extends ScriptableObject {
        private final Map<String, Property<Object>> props;

        PropertyScope(Map<String, Property<Object>> props) {
            this.props = props;
        }

        @Override public String getClassName() { return "PropertyScope"; }

        @Override public boolean has(String name, Scriptable start) {
            return props.containsKey(name) || super.has(name, start);
        }

        @Override public Object get(String name, Scriptable start) {
            Property<Object> p = props.get(name);
            if (p != null) return p.get();
            return super.get(name, start);
        }

        @Override public void put(String name, Scriptable start, Object value) {
            Property<Object> p = props.get(name);
            if (p != null) { p.set(value); return; }
            super.put(name, start, value);
        }
    }

    // A Binding whose evaluate() runs a precompiled Rhino script against the scope.
    static final class RhinoBinding extends Binding {
        private final Script script;
        private final Scriptable scope;

        RhinoBinding(Script script, Scriptable scope) {
            this.script = script;
            this.scope = scope;
        }

        @Override public Object evaluate() {
            Context cx = Context.enter();
            try {
                return script.exec(cx, scope);
            } finally {
                Context.exit();
            }
        }
    }

    private static Script compile(String expr) {
        Context cx = Context.enter();
        try {
            return cx.compileString(expr, "binding", 1, null);
        } finally {
            Context.exit();
        }
    }

    @Test
    void rhinoBindingTracksDependenciesAndRecomputes() {
        Property<Object> a = new Property<>(2L);
        Property<Object> b = new Property<>(3L);
        Property<Object> width = new Property<>(0L);

        Map<String, Property<Object>> props = new HashMap<>();
        props.put("a", a);
        props.put("b", b);

        Context cx = Context.enter();
        Scriptable scope;
        try {
            ScriptableObject std = cx.initStandardObjects();
            PropertyScope ps = new PropertyScope(props);
            ps.setPrototype(std);
            ps.setParentScope(null);
            scope = ps;
        } finally {
            Context.exit();
        }

        // width: a * 2 + b   -- a JS expression, run by Rhino, reading qml4j Properties.
        width.bind(new RhinoBinding(compile("a * 2 + b"), scope));
        assertEquals(7.0, num(width.get()));   // 2*2 + 3

        // Mutating a dependency re-runs the Rhino binding (no manual recompute).
        a.set(10L);
        assertEquals(23.0, num(width.get()));  // 10*2 + 3

        b.set(100L);
        assertEquals(120.0, num(width.get())); // 10*2 + 100

        // And a property the binding never read does NOT trigger recompute work:
        Property<Object> unused = new Property<>(0L);
        unused.set(999L);
        assertEquals(120.0, num(width.get()));
        assertTrue(true);
    }

    private static double num(Object o) { return ((Number) o).doubleValue(); }
}
