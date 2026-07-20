package io.github.timer_err.qml4j.compiler.bytecode.rhino;

import io.github.timer_err.qml4j.compiler.bytecode.AliasRef;
import io.github.timer_err.qml4j.compiler.bytecode.CompileScope;
import io.github.timer_err.qml4j.engine.QObject;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.engine.js.JsRuntime;
import io.github.timer_err.qml4j.engine.js.RhinoClosure;
import org.mozilla.javascript.RhinoException;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.timer_err.qml4j.compiler.bytecode.asm.Bytecode.pushStringArray;

// Decides whether a binding/handler/function body can run on the Rhino backend and
// collects what each body carries to its runtime QmlScope. Every free name a body
// reads or writes must be one the QmlScope can resolve -- a scene id, a declared or
// inherited property field, a root function, a Java method, a per-binding alias or
// singleton, or a shared JS global. Anything else is a compile error (there is no
// second backend to fall back to). Stateless apart from the compile-time scope
// stack it consults via CompileScope.
public final class RhinoScope {

    private RhinoScope() {}

    private static final Set<String> JS_GLOBALS = new HashSet<>(Arrays.asList(
        "Qt", "Math", "JSON", "console", "Easing", "Text", "TextInput", "TextEdit",
        "Font", "Item", "Flickable", "Animation", "Canvas", "ListView", "GridView", "RotationAnimation",
        "Image", "Gradient", "TapHandler", "PointerHandler", "Drag", "Window", "Component",
        "Object", "Array", "String", "Number", "Boolean", "Date", "RegExp",
        "parseInt", "parseFloat", "isNaN", "isFinite", "NaN", "Infinity", "undefined"));

    // Context properties injected by the host (QML's setContextProperty): resolved at
    // runtime off the shared globals scope, so the compiler just needs to accept them.
    private static final Set<String> CONTEXT_PROPERTIES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void registerContextProperty(String name) {
        CONTEXT_PROPERTIES.add(name);
    }

    // --- Source-based variants (free identifiers from Rhino's own parse, not the
    // QML AST). These are replacing the AST-based ones below as the grammar stops
    // defining a JS subset. `bound` is the set of names already in scope (signal /
    // function parameters). ---

    public static boolean canHandle(String source, Set<String> bound, Class<?> outerType,
                                    Map<String, Class<? extends QObject>> idTypes,
                                    Map<String, String> declaredProps,
                                    Map<String, Integer> rootFunctions,
                                    Set<String> customSignals,
                                    Map<String, AliasRef> aliases) {
        if (RhinoFreeVars.usesBareQtBinding(source)) return false;
        for (String name : RhinoFreeVars.collect(source, bound)) {
            if (!resolvable(name, outerType, idTypes, declaredProps, rootFunctions,
                            customSignals, aliases)) {
                return false;
            }
        }
        return true;
    }

    public static void require(String source, Set<String> bound, Class<?> outerType,
                               Map<String, Class<? extends QObject>> idTypes,
                               Map<String, String> declaredProps,
                               Map<String, Integer> rootFunctions,
                               Set<String> customSignals,
                               Map<String, AliasRef> aliases) {
        if (RhinoFreeVars.usesBareQtBinding(source)) {
            throw new IllegalArgumentException("Qt.binding(expr) must use the arrow form Qt.binding(() => expr)");
        }
        for (String name : RhinoFreeVars.collect(source, bound)) {
            if (!resolvable(name, outerType, idTypes, declaredProps, rootFunctions,
                            customSignals, aliases)) {
                throw new IllegalArgumentException("unknown identifier: " + name);
            }
        }
    }

    public static Map<String, Class<? extends QObject>> collectSingletonsFrom(String source) {
        Map<String, Class<? extends QObject>> m = new LinkedHashMap<>();
        for (String n : RhinoFreeVars.collect(source, Collections.emptySet())) {
            if (isSingleton(n)) m.put(n, CompileScope.currentSingletonClass(n));
        }
        return m;
    }

    public static Map<String, AliasRef> collectAliasesFrom(String source, Map<String, AliasRef> aliases) {
        if (aliases.isEmpty()) return Collections.emptyMap();
        Map<String, AliasRef> m = new LinkedHashMap<>();
        for (String n : RhinoFreeVars.collect(source, Collections.emptySet())) {
            AliasRef a = aliases.get(n);
            if (a != null) m.put(n, a);
        }
        return m;
    }

    private static boolean resolvable(String name, Class<?> outerType,
                                      Map<String, Class<? extends QObject>> idTypes,
                                      Map<String, String> declaredProps,
                                      Map<String, Integer> rootFunctions,
                                      Set<String> customSignals,
                                      Map<String, AliasRef> aliases) {
        if (aliases.containsKey(name)) return true;
        if (isSingleton(name)) return true;
        if (CompileScope.inDelegateScope()) {
            // In a delegate, index/modelData/local-id/enclosing-scope names resolve at
            // runtime via DelegateScope.delegateLookup (matching canHandle's delegate
            // branch).
            return true;
        }
        if (idTypes.containsKey(name) || declaredProps.containsKey(name)
                || rootFunctions.containsKey(name) || customSignals.contains(name)
                || JS_GLOBALS.contains(name) || CONTEXT_PROPERTIES.contains(name)) {
            return true;
        }
        try {
            outerType.getField(name);
            return true;
        } catch (NoSuchFieldException ignore) {
        }
        for (Method m : outerType.getMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return isChangedSignal(name, outerType, declaredProps);
    }

    // Qt's implicit per-property <prop>Changed() signal: callable for any declared or
    // inherited property. Resolved at runtime via QmlScope.changedSignal.
    private static boolean isChangedSignal(String name, Class<?> outerType,
                                           Map<String, String> declaredProps) {
        if (!name.endsWith("Changed") || name.length() == "Changed".length()) return false;
        String base = name.substring(0, name.length() - "Changed".length());
        if (declaredProps.containsKey(base)) return true;
        try {
            return Property.class.isAssignableFrom(outerType.getField(base).getType());
        } catch (NoSuchFieldException ignore) {
            return false;
        }
    }

    // Whether `name` resolves to a QML singleton (Theme, ...). Gated on an upper-case
    // initial (singletons/types are capitalized) so plain property/id identifiers don't
    // trigger a type-resolution attempt.
    public static boolean isSingleton(String name) {
        if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) return false;
        CompileScope.tryResolveType(name);
        return CompileScope.currentSingletonClass(name) != null;
    }

    // Pushes the alias specs (String[] of "name\0targetId\0targetProperty") for a binding,
    // so QmlScope can expand each alias to its target id's property at runtime. The NUL
    // separator can't appear in identifiers, so QmlScope.aliasMap splits on it cleanly.
    public static void pushAliases(MethodVisitor mv, Map<String, AliasRef> aliases) {
        List<String> specs = new ArrayList<>();
        for (Map.Entry<String, AliasRef> e : aliases.entrySet()) {
            specs.add(e.getKey() + " " + e.getValue().targetId + " " + e.getValue().targetProperty);
        }
        pushStringArray(mv, specs);
    }

    // Pushes the singleton names (String[]) and their classes (Class[]) for a binding,
    // so the runtime QmlScope can resolve them per-binding without a global registry.
    public static void pushSingletons(MethodVisitor mv, Map<String, Class<? extends QObject>> singletons) {
        List<String> names = new ArrayList<>(singletons.keySet());
        pushStringArray(mv, names);
        mv.visitLdcInsn(names.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        for (int i = 0; i < names.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(i);
            mv.visitLdcInsn(Type.getObjectType(Type.getInternalName(singletons.get(names.get(i)))));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    public static void validateSource(String source, List<String> params) {
        validateCompiles(RhinoClosure.wrap(source, params));
    }

    public static void validateCompiles(String js) {
        try {
            JsRuntime.validate(js);
        } catch (RhinoException e) {
            throw new IllegalArgumentException("invalid JS: " + e.getMessage(), e);
        }
    }
}
