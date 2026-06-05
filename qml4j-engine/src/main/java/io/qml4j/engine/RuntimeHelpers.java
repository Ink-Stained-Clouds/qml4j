package io.qml4j.engine;

import io.qml4j.engine.binding.Binding;
import io.qml4j.engine.binding.DirtyQueue;
import io.qml4j.engine.binding.Property;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeHelpers {

    private RuntimeHelpers() {}

    public static boolean truthy(Object x) {
        if (x == null) return false;
        if (x instanceof Boolean) return (Boolean) x;
        if (x instanceof Number) {
            double d = ((Number) x).doubleValue();
            return d != 0.0 && !Double.isNaN(d);
        }
        if (x instanceof String) return !((String) x).isEmpty();
        return true;
    }

    public static double toNumber(Object x) {
        if (x == null) return 0.0;
        if (x instanceof Number) return ((Number) x).doubleValue();
        if (x instanceof Boolean) return ((Boolean) x) ? 1.0 : 0.0;
        if (x instanceof String) {
            String s = ((String) x).trim();
            if (s.isEmpty()) return 0.0;
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) { return Double.NaN; }
        }
        return Double.NaN;
    }

    private static boolean isIntegral(Object x) {
        return x instanceof Long || x instanceof Integer || x instanceof Short || x instanceof Byte;
    }

    private static Object numericOp(Object l, Object r, char op) {
        double a = toNumber(l);
        double b = toNumber(r);
        double res;
        switch (op) {
            case '+': res = a + b; break;
            case '-': res = a - b; break;
            case '*': res = a * b; break;
            case '/': res = a / b; break;
            case '%': res = a % b; break;
            default: throw new IllegalStateException(String.valueOf(op));
        }
        if (op != '/' && isIntegral(l) && isIntegral(r) && res == (long) res) {
            return (long) res;
        }
        return res;
    }

    public static Object add(Object l, Object r) {
        if (l instanceof String || r instanceof String) {
            return String.valueOf(l) + String.valueOf(r);
        }
        return numericOp(l, r, '+');
    }

    public static Object sub(Object l, Object r) { return numericOp(l, r, '-'); }
    public static Object mul(Object l, Object r) { return numericOp(l, r, '*'); }
    public static Object div(Object l, Object r) { return numericOp(l, r, '/'); }
    public static Object mod(Object l, Object r) { return numericOp(l, r, '%'); }

    private static int compareValues(Object l, Object r) {
        if (l instanceof String && r instanceof String) {
            return ((String) l).compareTo((String) r);
        }
        return Double.compare(toNumber(l), toNumber(r));
    }

    public static Object lt(Object l, Object r) { return compareValues(l, r) < 0; }
    public static Object le(Object l, Object r) { return compareValues(l, r) <= 0; }
    public static Object gt(Object l, Object r) { return compareValues(l, r) > 0; }
    public static Object ge(Object l, Object r) { return compareValues(l, r) >= 0; }

    public static Object eq(Object l, Object r) {
        if (l == null || r == null) return l == r;
        if (l instanceof Number && r instanceof Number) {
            return toNumber(l) == toNumber(r);
        }
        if (l.getClass() == r.getClass()) return l.equals(r);
        return false;
    }

    public static Object neq(Object l, Object r) { return !((Boolean) eq(l, r)); }

    public static Object eqStrict(Object l, Object r) {
        if (l == r) return true;
        if (l == null || r == null) return false;
        // QML/JS has a single number type: 400 === 400.0 is true. Compare any
        // two Numbers by value, regardless of Java box class (a tween can leave
        // an int property holding a Double).
        if (l instanceof Number && r instanceof Number) {
            return toNumber(l) == toNumber(r);
        }
        if (l.getClass() != r.getClass()) return false;
        return l.equals(r);
    }

    public static Object neqStrict(Object l, Object r) { return !((Boolean) eqStrict(l, r)); }

    public static Object and(Object l, Object r) { return truthy(l) ? r : l; }
    public static Object or(Object l, Object r) { return truthy(l) ? l : r; }

    public static Object bitAnd(Object l, Object r) { return (long) toNumber(l) & (long) toNumber(r); }
    public static Object bitOr(Object l, Object r) { return (long) toNumber(l) | (long) toNumber(r); }
    public static Object bitXor(Object l, Object r) { return (long) toNumber(l) ^ (long) toNumber(r); }

    public static Object neg(Object x) {
        if (isIntegral(x)) return -((Number) x).longValue();
        return -toNumber(x);
    }

    public static Object pos(Object x) {
        if (x instanceof Number) return x;
        return toNumber(x);
    }

    public static Object not(Object x) { return !truthy(x); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object writeMember(Object target, String name, Object value) {
        if (target == null) {
            throw new NullPointerException("cannot assign '" + name + "' on null target");
        }
        Class<?> c = target.getClass();
        Field f;
        try {
            f = c.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                "no member '" + name + "' on " + c.getName());
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
        try {
            Field cf = item.getClass().getField("children");
            Object v = cf.get(item);
            return v instanceof List ? (List<Object>) v : null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
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
            return readMember(qtColor(target), name);
        }
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(name);
        }
        Class<?> c = target.getClass();
        Field f;
        try {
            f = c.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                "no member '" + name + "' on " + c.getName());
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
        return val;
    }

    public static Object makeArray(Object[] elems) {
        ArrayList<Object> out = new ArrayList<>(elems.length);
        for (Object e : elems) out.add(e);
        return out;
    }

    public static Object makeObject(String[] keys, Object[] values) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i++) out.put(keys[i], values[i]);
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object readIndex(Object target, Object index) {
        if (target == null) {
            throw new NullPointerException("cannot index null");
        }
        if (target instanceof List) {
            int i = (int) toNumber(index);
            List list = (List) target;
            if (i < 0 || i >= list.size()) return null;
            return list.get(i);
        }
        if (target instanceof Map) {
            return ((Map) target).get(String.valueOf(index));
        }
        if (target instanceof String) {
            int i = (int) toNumber(index);
            String s = (String) target;
            if (i < 0 || i >= s.length()) return null;
            return String.valueOf(s.charAt(i));
        }
        return readMember(target, String.valueOf(index));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object writeIndex(Object target, Object index, Object value) {
        if (target == null) {
            throw new NullPointerException("cannot index null");
        }
        if (target instanceof List) {
            int i = (int) toNumber(index);
            List list = (List) target;
            while (list.size() <= i) list.add(null);
            list.set(i, value);
            return value;
        }
        if (target instanceof Map) {
            ((Map) target).put(String.valueOf(index), value);
            return value;
        }
        return writeMember(target, String.valueOf(index), value);
    }

    public static Object delegateContext(Object start, String name) {
        Object cur = start;
        Object delegateRoot = null;
        while (cur != null) {
            if (cur instanceof DelegateRoot) {
                delegateRoot = cur;
                // index / modelData / delegate-local ids live on the delegate root.
                if (hasMember(cur, name)) return readMember(cur, name);
                break;
            }
            cur = parentOf(cur);
        }
        // Not delegate-local: resolve against the enclosing scene -- the component
        // that declared the Repeater -- by walking up from above the delegate root
        // to the first item exposing the member (e.g. a Slider tick delegate reading
        // the Slider's `_colors`).
        Object outer = parentOf(delegateRoot != null ? delegateRoot : start);
        while (outer != null) {
            if (hasMember(outer, name)) return readMember(outer, name);
            outer = parentOf(outer);
        }
        throw new IllegalStateException(
            "delegate context '" + name + "' not found in parent chain");
    }

    public static boolean hasMember(Object o, String name) {
        if (o == null) return false;
        try {
            o.getClass().getField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
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

    public static Object callMethod(Object receiver, String name, Object[] args) {
        if (receiver == null) {
            throw new NullPointerException("cannot call '" + name + "' on null receiver");
        }
        Class<?> cls = receiver.getClass();
        int n = args.length;
        java.lang.reflect.Method exact = null;
        java.lang.reflect.Method varargs = null;
        for (java.lang.reflect.Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) continue;
            int pc = m.getParameterCount();
            if (pc == n && !m.isVarArgs()) { exact = m; break; }
            if (m.isVarArgs() && pc - 1 <= n) varargs = m;
        }
        if (exact == null && varargs == null && receiver instanceof QObject) {
            Callable c = ((QObject) receiver).__getFunction(name);
            if (c != null) return c.call(args);
        }
        try {
            if (exact != null) {
                Object[] coerced = coerceArgs(args, exact.getParameterTypes());
                return exact.invoke(receiver, coerced);
            }
            if (varargs != null) {
                int fixed = varargs.getParameterCount() - 1;
                Class<?>[] paramTypes = varargs.getParameterTypes();
                Object[] reshaped = new Object[fixed + 1];
                for (int i = 0; i < fixed; i++) reshaped[i] = coerce(args[i], paramTypes[i]);
                Class<?> varElem = paramTypes[fixed].getComponentType();
                Object[] rest = new Object[n - fixed];
                for (int i = 0; i < rest.length; i++) rest[i] = coerce(args[fixed + i], varElem);
                reshaped[fixed] = rest;
                return varargs.invoke(receiver, reshaped);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        // A signal invoked as a function: control.clicked() emits the signal.
        try {
            java.lang.reflect.Field f = cls.getField(name);
            if (Signal.class.isAssignableFrom(f.getType())) {
                ((Signal) f.get(receiver)).emit(args);
                return null;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
        }
        throw new IllegalArgumentException(
            "no method '" + name + "' with " + n + " args on " + cls.getName());
    }

    // The JS Math global. Always returns a double, matching JS number semantics.
    public static Object callMath(String name, Object[] a) {
        switch (name) {
            case "max": {
                double r = Double.NEGATIVE_INFINITY;
                for (Object o : a) r = Math.max(r, toNumber(o));
                return r;
            }
            case "min": {
                double r = Double.POSITIVE_INFINITY;
                for (Object o : a) r = Math.min(r, toNumber(o));
                return r;
            }
            case "abs":   return Math.abs(toNumber(a[0]));
            case "floor": return Math.floor(toNumber(a[0]));
            case "ceil":  return Math.ceil(toNumber(a[0]));
            case "round": return (double) Math.round(toNumber(a[0]));
            case "trunc": return (double) (long) toNumber(a[0]);
            case "sign":  return Math.signum(toNumber(a[0]));
            case "sqrt":  return Math.sqrt(toNumber(a[0]));
            case "pow":   return Math.pow(toNumber(a[0]), toNumber(a[1]));
            case "exp":   return Math.exp(toNumber(a[0]));
            case "log":   return Math.log(toNumber(a[0]));
            case "sin":   return Math.sin(toNumber(a[0]));
            case "cos":   return Math.cos(toNumber(a[0]));
            case "tan":   return Math.tan(toNumber(a[0]));
            case "atan2": return Math.atan2(toNumber(a[0]), toNumber(a[1]));
            case "hypot": return Math.hypot(toNumber(a[0]), toNumber(a[1]));
            case "random": return Math.random();
            default:
                throw new UnsupportedOperationException("Math." + name + " is not supported");
        }
    }

    public static String stringify(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return (String) v;
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                long lv = (long) d;
                if ((double) lv == d) return Long.toString(lv);
            }
            return Double.toString(d);
        }
        return String.valueOf(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void spreadInto(ArrayList<Object> out, Object value) {
        if (value == null) return;
        if (value instanceof List) {
            out.addAll((List) value);
            return;
        }
        if (value instanceof Object[]) {
            for (Object e : (Object[]) value) out.add(e);
            return;
        }
        if (value instanceof Iterable) {
            for (Object e : (Iterable) value) out.add(e);
            return;
        }
        if (value instanceof String) {
            String s = (String) value;
            for (int i = 0; i < s.length(); i++) out.add(String.valueOf(s.charAt(i)));
            return;
        }
        throw new IllegalArgumentException("cannot spread non-iterable: " + value.getClass().getName());
    }

    public static Object[] listToArray(ArrayList<Object> list) {
        return list.toArray(new Object[0]);
    }

    public static Object callQml(Object start, Object root, String name, Object[] args) {
        Object cur = start;
        while (cur instanceof QObject) {
            Callable c = ((QObject) cur).__getFunction(name);
            if (c != null) return c.call(args);
            cur = ((QObject) cur).__getQmlParent();
        }
        return callMethod(root, name, args);
    }

    public static Object callValue(Object callee, Object[] args) {
        if (callee == null) throw new NullPointerException("cannot call null");
        if (callee instanceof Callable) return ((Callable) callee).call(args);
        if (callee instanceof Runnable && args.length == 0) {
            ((Runnable) callee).run();
            return null;
        }
        throw new IllegalArgumentException("not callable: " + callee.getClass().getName());
    }

    // Qt.color(str): parse a #rgb / #rrggbb / #aarrggbb color into channels 0..1.
    public static Object qtColor(Object v) {
        if (v instanceof QColor) return v;
        String s = String.valueOf(v).trim();
        if (s.startsWith("#")) s = s.substring(1);
        long bits;
        try {
            bits = Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return new QColor(0, 0, 0, 1);
        }
        int r, g, b, a = 255;
        if (s.length() == 3) {
            r = (int) ((bits >> 8) & 0xF) * 17;
            g = (int) ((bits >> 4) & 0xF) * 17;
            b = (int) (bits & 0xF) * 17;
        } else if (s.length() == 8) {
            a = (int) ((bits >> 24) & 0xFF);
            r = (int) ((bits >> 16) & 0xFF);
            g = (int) ((bits >> 8) & 0xFF);
            b = (int) (bits & 0xFF);
        } else {
            r = (int) ((bits >> 16) & 0xFF);
            g = (int) ((bits >> 8) & 0xFF);
            b = (int) (bits & 0xFF);
        }
        return new QColor(r / 255.0, g / 255.0, b / 255.0, a / 255.0);
    }

    public static Object qtRgba(Object r, Object g, Object b, Object a) {
        int ri = clampByte((int) Math.round(toNumber(r) * 255.0));
        int gi = clampByte((int) Math.round(toNumber(g) * 255.0));
        int bi = clampByte((int) Math.round(toNumber(b) * 255.0));
        int ai = clampByte((int) Math.round(toNumber(a) * 255.0));
        return formatHex(ai, ri, gi, bi);
    }

    public static Object qtHsla(Object h, Object s, Object l, Object a) {
        double hh = wrap01(toNumber(h));
        double ss = clamp01(toNumber(s));
        double ll = clamp01(toNumber(l));
        double aa = clamp01(toNumber(a));
        double[] rgb = hslToRgb(hh, ss, ll);
        int ri = (int) Math.round(rgb[0] * 255.0);
        int gi = (int) Math.round(rgb[1] * 255.0);
        int bi = (int) Math.round(rgb[2] * 255.0);
        int ai = (int) Math.round(aa * 255.0);
        return formatHex(ai, ri, gi, bi);
    }

    public static void qtCallLater(Object work) {
        if (work == null) return;
        Runnable r = toRunnable(work);
        DirtyQueue dq = DirtyQueue.current();
        if (dq != null) dq.enqueue(r);
        else r.run();
    }

    private static Runnable toRunnable(Object work) {
        if (work instanceof Runnable) return (Runnable) work;
        if (work instanceof Binding) {
            Binding b = (Binding) work;
            return b::evaluate;
        }
        if (work instanceof Callable) {
            Callable c = (Callable) work;
            return () -> c.call(EMPTY_ARGS);
        }
        throw new IllegalArgumentException(
            "Qt.callLater requires Qt.binding(...), an arrow function, or a callable; got "
            + work.getClass().getName());
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double wrap01(double v) {
        double w = v - Math.floor(v);
        return w < 0 ? w + 1 : w;
    }

    private static String formatHex(int a, int r, int g, int b) {
        return String.format("#%02x%02x%02x%02x", a, r, g, b);
    }

    private static double[] hslToRgb(double h, double s, double l) {
        if (s == 0.0) return new double[]{l, l, l};
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        return new double[]{
            hueToRgb(p, q, h + 1.0 / 3.0),
            hueToRgb(p, q, h),
            hueToRgb(p, q, h - 1.0 / 3.0)
        };
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6;
        return p;
    }

    private static Object[] coerceArgs(Object[] args, Class<?>[] paramTypes) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = coerce(args[i], paramTypes[i]);
        return out;
    }

    private static Object coerce(Object value, Class<?> target) {
        if (value == null) return null;
        if (target == null || target.isInstance(value)) return value;
        if (value instanceof Number) {
            Number num = (Number) value;
            if (target == int.class || target == Integer.class) return num.intValue();
            if (target == long.class || target == Long.class) return num.longValue();
            if (target == double.class || target == Double.class) return num.doubleValue();
            if (target == float.class || target == Float.class) return num.floatValue();
            if (target == short.class || target == Short.class) return num.shortValue();
            if (target == byte.class || target == Byte.class) return num.byteValue();
        }
        if ((target == boolean.class || target == Boolean.class) && value instanceof Boolean) {
            return value;
        }
        if (target == String.class) return value.toString();
        return value;
    }
}
