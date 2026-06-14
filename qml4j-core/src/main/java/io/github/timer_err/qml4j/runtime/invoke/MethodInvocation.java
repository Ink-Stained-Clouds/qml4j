package io.github.timer_err.qml4j.runtime.invoke;

import io.github.timer_err.qml4j.engine.Callable;
import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.Signal;
import io.github.timer_err.qml4j.runtime.ClassCache;
import io.github.timer_err.qml4j.runtime.convert.Coercion;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Reflective method dispatch on a receiver: exact then varargs Java methods,
// falling back to a QML function, then to emitting a same-named signal. Method
// resolution is cached per (class, name, arity) -- the JS->Java call path runs
// thousands of times per frame (Canvas ctx.lineTo/arc), and an uncached getMethods
// scan allocates the whole method array each call.
public final class MethodInvocation {

    private MethodInvocation() {}

    public static Object callMethod(Object receiver, String name, Object[] args) {
        if (receiver == null) {
            throw new NullPointerException("cannot call '" + name + "' on null receiver");
        }
        Class<?> cls = receiver.getClass();
        int n = args.length;
        Resolved r = resolve(cls, name, n);
        if (r.exact == null && r.varargs == null && receiver instanceof QObject) {
            Callable c = ((QObject) receiver).__getFunction(name);
            if (c != null) return c.call(args);
        }
        try {
            if (r.exact != null) {
                Object[] coerced = Coercion.coerceArgs(args, r.exactParams);
                return r.exact.invoke(receiver, coerced);
            }
            if (r.varargs != null) {
                int fixed = r.varargsFixed;
                Object[] reshaped = new Object[fixed + 1];
                for (int i = 0; i < fixed; i++) reshaped[i] = Coercion.coerce(args[i], r.varargsParams[i]);
                Object[] rest = new Object[n - fixed];
                for (int i = 0; i < rest.length; i++) rest[i] = Coercion.coerce(args[fixed + i], r.varElem);
                reshaped[fixed] = rest;
                return r.varargs.invoke(receiver, reshaped);
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

    // Whether `cls` has any public method named `name`. JS member access probes this
    // for every `obj.name` that isn't a field, so the scan must not be per-access.
    public static boolean hasMethod(Class<?> cls, String name) {
        return METHOD_NAMES.get(cls).contains(name);
    }

    // Per-class resolution cache, keyed on Class identity -- correct across per-component
    // classloaders that mint same-named classes (a name key would collide into "object is
    // not an instance of declaring class").
    private static final ClassCache<ConcurrentHashMap<String, ConcurrentHashMap<Integer, Resolved>>> METHODS =
        new ClassCache<>(type -> new ConcurrentHashMap<>());

    private static final ClassCache<Set<String>> METHOD_NAMES =
        new ClassCache<>(type -> {
            Set<String> names = new HashSet<>();
            for (Method m : type.getMethods()) names.add(m.getName());
            return names;
        });

    private static Resolved resolve(Class<?> cls, String name, int argc) {
        return METHODS.get(cls)
            .computeIfAbsent(name, n -> new ConcurrentHashMap<>())
            .computeIfAbsent(argc, ac -> scan(cls, name, ac));
    }

    private static Resolved scan(Class<?> cls, String name, int argc) {
        Method exact = null;
        Method varargs = null;
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) continue;
            int pc = m.getParameterCount();
            if (pc == argc && !m.isVarArgs()) { exact = m; break; }
            if (m.isVarArgs() && pc - 1 <= argc) varargs = m;
        }
        return new Resolved(exact, varargs);
    }

    // A resolved overload pair plus its parameter types, so the call path never calls
    // getParameterTypes (which clones the array) again.
    private static final class Resolved {
        final Method exact;
        final Class<?>[] exactParams;
        final Method varargs;
        final Class<?>[] varargsParams;
        final int varargsFixed;
        final Class<?> varElem;

        Resolved(Method exact, Method varargs) {
            this.exact = exact;
            this.exactParams = exact != null ? exact.getParameterTypes() : null;
            this.varargs = varargs;
            if (varargs != null) {
                this.varargsParams = varargs.getParameterTypes();
                this.varargsFixed = varargs.getParameterCount() - 1;
                this.varElem = varargsParams[varargsFixed].getComponentType();
            } else {
                this.varargsParams = null;
                this.varargsFixed = 0;
                this.varElem = null;
            }
        }
    }
}
