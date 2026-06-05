package io.qml4j.engine.js;

import io.qml4j.engine.RuntimeHelpers;
import org.mozilla.javascript.Scriptable;

// The top scope a binding's JS runs in. Bare identifiers resolve against the QML
// scope: first the this-object's members, then the component root's (ids + declared
// props). Member reads route through RuntimeHelpers.readMember, so Property reads
// register the reactive dependency just like the ASM backend. Names not found here
// return NOT_FOUND, so Rhino falls through to the shared globals (Math, etc.) on the
// parent scope.
//
// First-cut scope: ids + this/root properties only. Singletons, Qt, enums, signal
// params and JS locals come in later migration phases (the compiler's canHandle
// predicate keeps those off this path for now).
public final class QmlScope implements Scriptable {

    private final Object outer;
    private final Object root;
    private Scriptable parent;
    private Scriptable prototype;

    public QmlScope(Object outer, Object root, Scriptable globals) {
        this.outer = outer;
        this.root = root;
        this.parent = globals;
    }

    private Object owner(String name) {
        if (RuntimeHelpers.hasMember(outer, name)) return outer;
        if (root != outer && RuntimeHelpers.hasMember(root, name)) return root;
        return null;
    }

    @Override public Object get(String name, Scriptable start) {
        Object o = owner(name);
        if (o == null) return NOT_FOUND;
        return JsWrap.toJs(RuntimeHelpers.readMember(o, name), this);
    }

    @Override public boolean has(String name, Scriptable start) {
        return owner(name) != null;
    }

    @Override public void put(String name, Scriptable start, Object value) {
        Object o = owner(name);
        if (o != null) RuntimeHelpers.writeMember(o, name, JsWrap.toJava(value));
    }

    @Override public Object get(int index, Scriptable start) { return NOT_FOUND; }
    @Override public boolean has(int index, Scriptable start) { return false; }
    @Override public void put(int index, Scriptable start, Object value) {}
    @Override public void delete(String name) {}
    @Override public void delete(int index) {}

    @Override public String getClassName() { return "QmlScope"; }
    @Override public Object getDefaultValue(Class<?> hint) { return toString(); }
    @Override public Scriptable getParentScope() { return parent; }
    @Override public void setParentScope(Scriptable s) { this.parent = s; }
    @Override public Scriptable getPrototype() { return prototype; }
    @Override public void setPrototype(Scriptable s) { this.prototype = s; }
    @Override public Object[] getIds() { return new Object[0]; }
    @Override public boolean hasInstance(Scriptable instance) { return false; }
}
