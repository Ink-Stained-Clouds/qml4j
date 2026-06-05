package io.qml4j.engine.js;

import io.qml4j.engine.RuntimeHelpers;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

// The shared JS global scope for the Rhino backend: standard objects (Math, JSON,
// parseInt, ...) plus QML's Qt namespace (enum constants + Qt.rgba/hsla/color
// factories bridged to RuntimeHelpers) and the Text/Font/Easing enum namespaces and
// console. Built once and shared as the parent scope of every QmlScope.
//
// Enum tables are duplicated from the compiler's ExpressionCodegen.ENUMS for now;
// they'll be unified when the ASM backend is deleted (Phase 6).
public final class QtGlobals {

    private QtGlobals() {}

    public static Scriptable build(Context cx) {
        ScriptableObject scope = cx.initStandardObjects();

        NativeObject qt = enumObject(QT);
        qt.put("rgba", qt, fn("rgba", 4, a -> RuntimeHelpers.qtRgba(arg(a, 0), arg(a, 1), arg(a, 2), arg(a, 3))));
        qt.put("hsla", qt, fn("hsla", 4, a -> RuntimeHelpers.qtHsla(arg(a, 0), arg(a, 1), arg(a, 2), arg(a, 3))));
        qt.put("color", qt, fn("color", 1, a -> RuntimeHelpers.qtColor(arg(a, 0))));
        qt.put("callLater", qt, callLater());
        scope.put("Qt", scope, qt);

        scope.put("Text", scope, enumObject(TEXT));
        scope.put("Font", scope, enumObject(FONT));
        scope.put("Easing", scope, enumObject(EASING));

        NativeObject console = new NativeObject();
        console.put("log", console, fn("log", 1, a -> { System.out.println(join(a)); return null; }));
        console.put("warn", console, fn("warn", 1, a -> { System.out.println(join(a)); return null; }));
        scope.put("console", scope, console);

        return scope;
    }

    // Qt.callLater(fn): defer the JS function to the next DirtyQueue flush. The
    // function is re-entered under a fresh Rhino context against its captured scope.
    // (org.mozilla.javascript.Function is FQN'd here -- it clashes with the imported
    // java.util.function.Function used by fn().)
    private static BaseFunction callLater() {
        return new BaseFunction() {
            @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                Object f = args.length > 0 ? args[0] : null;
                if (f instanceof org.mozilla.javascript.Function) {
                    org.mozilla.javascript.Function jf = (org.mozilla.javascript.Function) f;
                    Scriptable home = jf.getParentScope() != null ? jf.getParentScope() : scope;
                    RuntimeHelpers.qtCallLater((Runnable) () -> {
                        Context c = JsRuntime.enter();
                        try {
                            jf.call(c, home, home, ScriptRuntime.emptyArgs);
                        } finally {
                            Context.exit();
                        }
                    });
                }
                return null;
            }
            @Override public int getArity() { return 1; }
            @Override public String getFunctionName() { return "callLater"; }
        };
    }

    private static NativeObject enumObject(Map<String, Long> members) {
        NativeObject o = new NativeObject();
        for (Map.Entry<String, Long> e : members.entrySet()) {
            o.put(e.getKey(), o, e.getValue());
        }
        return o;
    }

    private static BaseFunction fn(String name, int arity, Function<Object[], Object> impl) {
        return new BaseFunction() {
            @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                return JsWrap.toJs(impl.apply(args), scope);
            }
            @Override public int getArity() { return arity; }
            @Override public String getFunctionName() { return name; }
        };
    }

    private static Object arg(Object[] args, int i) {
        return i < args.length ? JsWrap.toJava(args[i]) : null;
    }

    private static String join(Object[] args) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) b.append(' ');
            b.append(JsWrap.toJava(args[i]));
        }
        return b.toString();
    }

    private static Map<String, Long> map(Object... kv) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Long) kv[i + 1]);
        return m;
    }

    private static final Map<String, Long> QT = map(
        "Horizontal", 1L, "Vertical", 2L,
        "NoButton", 0L, "LeftButton", 1L, "RightButton", 2L, "MiddleButton", 4L,
        "AlignLeft", 1L, "AlignRight", 2L, "AlignHCenter", 4L, "AlignJustify", 8L,
        "AlignTop", 32L, "AlignBottom", 64L, "AlignVCenter", 128L, "AlignBaseline", 256L,
        "AlignCenter", 132L,
        "ArrowCursor", 0L, "IBeamCursor", 4L, "PointingHandCursor", 13L);

    private static final Map<String, Long> TEXT = map(
        "AlignLeft", 1L, "AlignRight", 2L, "AlignHCenter", 4L, "AlignJustify", 8L,
        "AlignTop", 32L, "AlignBottom", 64L, "AlignVCenter", 128L,
        "NoWrap", 0L, "WordWrap", 1L, "WrapAnywhere", 3L, "Wrap", 4L,
        "ElideNone", 0L, "ElideLeft", 1L, "ElideMiddle", 2L, "ElideRight", 3L);

    private static final Map<String, Long> FONT = map(
        "Thin", 0L, "ExtraLight", 12L, "Light", 25L, "Normal", 50L, "Medium", 57L,
        "DemiBold", 63L, "Bold", 75L, "ExtraBold", 81L, "Black", 87L,
        "MixedCase", 0L, "AllUppercase", 1L, "AllLowercase", 2L, "SmallCaps", 3L, "Capitalize", 4L);

    private static final Map<String, Long> EASING = map(
        "Linear", 0L, "InQuad", 1L, "OutQuad", 2L, "InOutQuad", 3L, "OutInQuad", 4L,
        "InCubic", 5L, "OutCubic", 6L, "InOutCubic", 7L, "OutInCubic", 8L,
        "InQuart", 9L, "OutQuart", 10L, "InOutQuart", 11L,
        "InSine", 13L, "OutSine", 14L, "InOutSine", 15L,
        "InBack", 29L, "OutBack", 30L, "InOutBack", 31L);
}
