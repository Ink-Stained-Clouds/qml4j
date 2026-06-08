package io.qml4j.runtime.member;

import io.qml4j.engine.binding.Property;
import io.qml4j.engine.binding.ObservableList;
import io.qml4j.runtime.convert.Coercion;
import io.qml4j.runtime.qt.QColor;
import io.qml4j.runtime.qt.QtColorFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Reflective member and index access over QObjects, Maps, Lists and Strings.
// Property fields are transparently read/written; a color's .r/.g/.b/.a and a
// collection's .length are resolved virtually.
public final class MemberAccess {

    private MemberAccess() {}

    // Public-field lookup is on the hot path: every JS member read/write resolves a
    // field by name, and has/resolves probes it before each access. getField walks the
    // type hierarchy and allocates each call, so cache the result per (class, name).
    // ClassValue keys on Class identity -- correct across per-component classloaders
    // that mint same-named classes -- and releases entries when a class unloads, so it
    // never pins a hot-reloaded component.
    private static final ClassValue<ConcurrentHashMap<String, Field>> FIELDS =
        new ClassValue<ConcurrentHashMap<String, Field>>() {
            @Override protected ConcurrentHashMap<String, Field> computeValue(Class<?> type) {
                return new ConcurrentHashMap<>();
            }
        };

    // Cached for names with no matching field, so absence is a cache hit too.
    private static final Field ABSENT = absentSentinel();

    private static Field absentSentinel() {
        try {
            return MemberAccess.class.getDeclaredField("ABSENT");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Field field(Class<?> type, String name) {
        Field f = FIELDS.get(type).computeIfAbsent(name, n -> lookupField(type, n));
        return f == ABSENT ? null : f;
    }

    private static Field lookupField(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException e) {
            return ABSENT;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object writeMember(Object target, String name, Object value) {
        if (target == null) {
            throw new NullPointerException("cannot assign '" + name + "' on null target");
        }
        Class<?> c = target.getClass();
        Field f = field(c, name);
        if (f == null) {
            throw new IllegalArgumentException("no member '" + name + "' on " + c.getName());
        }
        try {
            Object cur = f.get(target);
            if (cur instanceof Property) {
                // Imperative reparenting (item.parent = x) must move the node
                // between children lists, not just flip the property -- the scene
                // is drawn via children. Construction sets parent via bytecode,
                // not this path, so it's unaffected.
                if ("parent".equals(name)) {
                    reparent(target, ((Property) cur).peek(), value);
                }
                ((Property) cur).set(value);
            } else {
                f.set(target, value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void reparent(Object child, Object oldParent, Object newParent) {
        List<Object> oldKids = childrenOf(oldParent);
        if (oldKids != null) oldKids.remove(child);
        List<Object> newKids = childrenOf(newParent);
        if (newKids != null && !newKids.contains(child)) newKids.add(child);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> childrenOf(Object item) {
        if (item == null) return null;
        Field cf = field(item.getClass(), "children");
        if (cf == null) return null;
        try {
            Object v = cf.get(item);
            return v instanceof List ? (List<Object>) v : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public static Object readMember(Object target, String name) {
        if (target == null) return null;
        if ("length".equals(name)) {
            if (target instanceof String) return (long) ((String) target).length();
            if (target instanceof List) return (long) ((List<?>) target).size();
            if (target instanceof Map) return (long) ((Map<?, ?>) target).size();
        }
        // A color value's channels: Qt exposes .r/.g/.b/.a on a color. Our colors
        // are strings ("#rrggbb"); resolve the channel on demand.
        if (target instanceof QColor && (name.length() == 1)) {
            QColor q = (QColor) target;
            switch (name) {
                case "r": return q.r; case "g": return q.g;
                case "b": return q.b; case "a": return q.a;
                default: break;
            }
        }
        if (target instanceof String && name.length() == 1
            && ("r".equals(name) || "g".equals(name) || "b".equals(name) || "a".equals(name))) {
            return readMember(QtColorFactory.qtColor(target), name);
        }
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(name);
        }
        Class<?> c = target.getClass();
        Field f = field(c, name);
        if (f == null) {
            throw new IllegalArgumentException("no member '" + name + "' on " + c.getName());
        }
        Object val;
        try {
            val = f.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        if (val instanceof Property) {
            return ((Property<?>) val).get();
        }
        // A binding that reads an observable list (item.children) depends on its structure,
        // so a child added later re-evaluates it (a container sizing to its content).
        if (val instanceof ObservableList) {
            ((ObservableList<?>) val).trackRead();
        }
        return val;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readIndex(Object target, Object index) {
        if (target == null) {
            throw new NullPointerException("cannot index null");
        }
        if (target instanceof List) {
            int i = (int) Coercion.toNumber(index);
            List list = (List) target;
            if (i < 0 || i >= list.size()) return null;
            return list.get(i);
        }
        if (target instanceof Map) {
            return ((Map) target).get(String.valueOf(index));
        }
        if (target instanceof String) {
            int i = (int) Coercion.toNumber(index);
            String s = (String) target;
            if (i < 0 || i >= s.length()) return null;
            return String.valueOf(s.charAt(i));
        }
        return readMember(target, String.valueOf(index));
    }

    public static boolean hasMember(Object o, String name) {
        if (o == null) return false;
        return field(o.getClass(), name) != null;
    }

    // The Property field named `name` on `o`, or null if `o` has no such Property field.
    // Used to emit Qt's implicit <prop>Changed() signal off a bare identifier.
    public static Property<?> propertyOf(Object o, String name) {
        if (o == null) return null;
        Field f = field(o.getClass(), name);
        if (f == null || !Property.class.isAssignableFrom(f.getType())) return null;
        try {
            return (Property<?>) f.get(o);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // Whether `name` is a Property field on `o` (not an id/signal/group field). The
    // delegate scope resolves a name to the binding's own item only when it's a real
    // property -- so a compound child's leaked internal id (Ripple's `id: root`) does
    // not shadow the enclosing component's same-named id.
    public static boolean hasProperty(Object o, String name) {
        if (o == null) return false;
        Field f = field(o.getClass(), name);
        return f != null && Property.class.isAssignableFrom(f.getType());
    }
}
