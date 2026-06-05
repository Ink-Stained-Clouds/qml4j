package io.qml4j.engine.js;

import io.qml4j.engine.QObject;
import io.qml4j.engine.RuntimeHelpers;
import io.qml4j.engine.Signal;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Bridges values across the Java <-> Rhino boundary for the embedded-JS backend.
// Primitives pass through; Java objects (QObject/QColor/Map/List) are exposed to JS
// as a JavaMember scriptable whose member/index access routes through RuntimeHelpers
// (so item.width reads the Property -- and records the dependency -- color.r resolves
// a channel, list[0] indexes, etc.).
public final class JsWrap {

    private JsWrap() {}

    public static Object toJs(Object v, Scriptable scope) {
        if (v == null) return null;
        if (v instanceof Number || v instanceof Boolean) return v;
        if (v instanceof CharSequence) return v.toString();
        return new JavaMember(v, scope);
    }

    public static Object toJava(Object v) {
        if (v == null || v == Undefined.instance) return null;
        if (v instanceof Wrapper) return ((Wrapper) v).unwrap();
        if (v instanceof CharSequence) return v.toString();
        if (v instanceof Boolean) return v;
        if (v instanceof Double) {
            double d = (Double) v;
            if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d)
                    && Math.abs(d) < 9.007199254740992e15) {
                return (long) d;
            }
            return d;
        }
        if (v instanceof Number) return v;
        if (v instanceof NativeArray) {
            NativeArray a = (NativeArray) v;
            List<Object> out = new ArrayList<>();
            for (Object id : a.getIds()) {
                if (id instanceof Integer) out.add(toJava(a.get((Integer) id, a)));
            }
            return out;
        }
        if (v instanceof NativeObject) {
            NativeObject o = (NativeObject) v;
            Map<String, Object> out = new LinkedHashMap<>();
            for (Object id : o.getIds()) {
                String k = String.valueOf(id);
                out.put(k, toJava(o.get(k, o)));
            }
            return out;
        }
        return v;
    }

    // A Rhino view over an arbitrary Java object, delegating member/index reads and
    // writes to RuntimeHelpers (same semantics as the ASM backend's readMember etc).
    static final class JavaMember implements Scriptable, Wrapper {
        private final Object target;
        private final Scriptable parent;

        JavaMember(Object target, Scriptable parent) {
            this.target = target;
            this.parent = parent;
        }

        // So a binding that yields a wrapped Java object (e.g. `parent: foo`) unwraps
        // back to the real object on the way out, not the JavaMember view.
        @Override public Object unwrap() { return target; }

        @Override public Object get(String name, Scriptable start) {
            if (resolves(name)) {
                Object v = RuntimeHelpers.readMember(target, name);
                // A signal member supports both forms QML uses: a direct emit
                // (`control.clicked()`) and an explicit `.emit(...)` / `.connect(...)`.
                if (v instanceof Signal) return new SignalRef((Signal) v, parent);
                return toJs(v, parent);
            }
            if (isCallable(target, name)) {
                return new BoundMethod(target, name, parent);
            }
            return NOT_FOUND;
        }

        @Override public Object[] getIds() {
            if (target instanceof Map) return ((Map<?, ?>) target).keySet().toArray();
            if (target instanceof List) {
                int n = ((List<?>) target).size();
                Object[] ids = new Object[n];
                for (int i = 0; i < n; i++) ids[i] = i;
                return ids;
            }
            return new Object[0];
        }

        @Override public Object get(int index, Scriptable start) {
            return toJs(RuntimeHelpers.readIndex(target, (long) index), parent);
        }

        @Override public void put(String name, Scriptable start, Object value) {
            RuntimeHelpers.writeMember(target, name, toJava(value));
        }

        @Override public boolean has(String name, Scriptable start) {
            return resolves(name) || isCallable(target, name);
        }

        @Override public boolean has(int index, Scriptable start) { return true; }

        // Whether `name` reads as a data member -- a map key, or a reflective/virtual
        // member RuntimeHelpers.readMember resolves. Map keys are not reflective
        // fields, so they must be checked against the map directly or for-in / `in`
        // would miss them.
        private boolean resolves(String name) {
            if (target instanceof Map) return ((Map<?, ?>) target).containsKey(name);
            return RuntimeHelpers.hasMember(target, name) || isVirtual(name);
        }

        // length/r/g/b/a aren't reflective fields but RuntimeHelpers.readMember resolves them.
        private boolean isVirtual(String name) {
            return "length".equals(name) || (name.length() == 1 && "rgba".contains(name));
        }

        @Override public String getClassName() { return "JavaMember"; }
        @Override public Object getDefaultValue(Class<?> hint) { return target.toString(); }
        @Override public Scriptable getParentScope() { return parent; }
        @Override public void setParentScope(Scriptable s) {}
        @Override public Scriptable getPrototype() { return null; }
        @Override public void setPrototype(Scriptable s) {}
        @Override public void put(int index, Scriptable start, Object value) {}
        @Override public void delete(String name) {}
        @Override public void delete(int index) {}
        @Override public boolean hasInstance(Scriptable instance) { return false; }
    }

    // Whether `target.name` is invokable: a QML function on a QObject, or a public
    // Java method. Used by both the JavaMember member-access path and QmlScope's
    // bare-call resolution.
    static boolean isCallable(Object target, String name) {
        if (target instanceof QObject && ((QObject) target).__getFunction(name) != null) return true;
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }

    // A method reference obtained from a JavaMember (obj.method); calling it routes to
    // RuntimeHelpers.callMethod, which dispatches to a Java method or a QML function.
    static final class BoundMethod extends BaseFunction {
        private final Object target;
        private final String name;
        private final Scriptable parent;

        BoundMethod(Object target, String name, Scriptable parent) {
            this.target = target;
            this.name = name;
            this.parent = parent;
        }

        @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Object[] ja = new Object[args.length];
            for (int i = 0; i < args.length; i++) ja[i] = toJava(args[i]);
            return toJs(RuntimeHelpers.callMethod(target, name, ja), parent);
        }
    }

    // A QML signal exposed to JS so both forms work: calling it emits
    // (`control.clicked(args)`), and its `.emit`/`.connect`/... members resolve to the
    // Signal's Java methods (`r.pinged.emit(args)`).
    static final class SignalRef extends BaseFunction implements Wrapper {
        private final Signal signal;
        private final Scriptable parent;

        SignalRef(Signal signal, Scriptable parent) {
            this.signal = signal;
            this.parent = parent;
        }

        @Override public Object unwrap() { return signal; }

        @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Object[] ja = new Object[args.length];
            for (int i = 0; i < args.length; i++) ja[i] = toJava(args[i]);
            signal.emit(ja);
            return Undefined.instance;
        }

        @Override public Object get(String name, Scriptable start) {
            if (isCallable(signal, name)) return new BoundMethod(signal, name, parent);
            return super.get(name, start);
        }

        @Override public boolean has(String name, Scriptable start) {
            return isCallable(signal, name) || super.has(name, start);
        }
    }
}
