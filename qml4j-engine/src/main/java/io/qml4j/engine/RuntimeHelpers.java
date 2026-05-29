package io.qml4j.engine;

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
                ((Property) cur).set(value);
            } else {
                f.set(target, value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    public static Object readMember(Object target, String name) {
        if (target == null) return null;
        if ("length".equals(name)) {
            if (target instanceof String) return (long) ((String) target).length();
            if (target instanceof List) return (long) ((List<?>) target).size();
            if (target instanceof Map) return (long) ((Map<?, ?>) target).size();
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
        throw new IllegalArgumentException(
            "no method '" + name + "' with " + n + " args on " + cls.getName());
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
