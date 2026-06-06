package io.qml4j.runtime.invoke;

import io.qml4j.engine.Callable;
import io.qml4j.engine.QObject;
import io.qml4j.engine.Signal;
import io.qml4j.runtime.convert.Coercion;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Reflective method dispatch on a receiver: exact then varargs Java methods,
// falling back to a QML function, then to emitting a same-named signal.
public final class MethodInvocation {

    private MethodInvocation() {}

    public static Object callMethod(Object receiver, String name, Object[] args) {
        if (receiver == null) {
            throw new NullPointerException("cannot call '" + name + "' on null receiver");
        }
        Class<?> cls = receiver.getClass();
        int n = args.length;
        Method exact = null;
        Method varargs = null;
        for (Method m : cls.getMethods()) {
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
                Object[] coerced = Coercion.coerceArgs(args, exact.getParameterTypes());
                return exact.invoke(receiver, coerced);
            }
            if (varargs != null) {
                int fixed = varargs.getParameterCount() - 1;
                Class<?>[] paramTypes = varargs.getParameterTypes();
                Object[] reshaped = new Object[fixed + 1];
                for (int i = 0; i < fixed; i++) reshaped[i] = Coercion.coerce(args[i], paramTypes[i]);
                Class<?> varElem = paramTypes[fixed].getComponentType();
                Object[] rest = new Object[n - fixed];
                for (int i = 0; i < rest.length; i++) rest[i] = Coercion.coerce(args[fixed + i], varElem);
                reshaped[fixed] = rest;
                return varargs.invoke(receiver, reshaped);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        // A signal invoked as a function: control.clicked() emits the signal.
        try {
            Field f = cls.getField(name);
            if (Signal.class.isAssignableFrom(f.getType())) {
                ((Signal) f.get(receiver)).emit(args);
                return null;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
        }
        throw new IllegalArgumentException(
            "no method '" + name + "' with " + n + " args on " + cls.getName());
    }
}
