package io.qml4j.engine;

import java.lang.reflect.Field;

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

    public static Object readMember(Object target, String name) {
        if (target == null) return null;
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
}
