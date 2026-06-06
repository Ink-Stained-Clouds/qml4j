package io.qml4j.runtime.member;

import io.qml4j.engine.DelegateRoot;
import io.qml4j.engine.QObject;
import io.qml4j.engine.binding.Property;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Delegate-scope name resolution shared by the Rhino QmlScope: walk to the
// DelegateRoot for index/modelData/delegate-local ids, then above it to the
// enclosing scene for outer-scope members.
public final class DelegateScope {

    private DelegateScope() {}

    // Sentinel for delegateLookup when a name is nowhere in the delegate's chain.
    public static final Object DELEGATE_ABSENT = new Object();

    // The first object up the delegate's parent chain on which `name` is callable -- a
    // QML function or a Java method. Mirrors the lookup walk so a bare call `foo()` in a
    // delegate resolves to a delegate-local or enclosing function. Null if none.
    public static Object delegateCallableOwner(Object start, String name) {
        Object cur = start;
        while (cur != null) {
            if (isCallableMember(cur, name)) return cur;
            cur = parentOf(cur);
        }
        return null;
    }

    private static boolean isCallableMember(Object o, String name) {
        if (o instanceof QObject && ((QObject) o).__getFunction(name) != null) return true;
        for (Method m : o.getClass().getMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }

    public static Object delegateLookup(Object start, String name) {
        Object cur = start;
        Object delegateRoot = null;
        while (cur != null) {
            if (cur instanceof DelegateRoot) {
                delegateRoot = cur;
                if (MemberAccess.hasMember(cur, name)) return MemberAccess.readMember(cur, name);
                break;
            }
            cur = parentOf(cur);
        }
        Object outer = parentOf(delegateRoot != null ? delegateRoot : start);
        while (outer != null) {
            if (MemberAccess.hasMember(outer, name)) return MemberAccess.readMember(outer, name);
            outer = parentOf(outer);
        }
        return DELEGATE_ABSENT;
    }

    private static Object parentOf(Object node) {
        Field f;
        try {
            f = node.getClass().getField("parent");
        } catch (NoSuchFieldException e) {
            return null;
        }
        Object v;
        try {
            v = f.get(node);
        } catch (IllegalAccessException e) {
            return null;
        }
        if (v instanceof Property) return ((Property<?>) v).peek();
        return v;
    }
}
