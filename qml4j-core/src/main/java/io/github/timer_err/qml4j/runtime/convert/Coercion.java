package io.github.timer_err.qml4j.runtime.convert;

// Value coercion shared by the runtime: JS number semantics and reflective
// argument conversion to a target method's parameter types.
public final class Coercion {

    private Coercion() {}

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

    public static Object[] coerceArgs(Object[] args, Class<?>[] paramTypes) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = coerce(args[i], paramTypes[i]);
        return out;
    }

    public static Object coerce(Object value, Class<?> target) {
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
