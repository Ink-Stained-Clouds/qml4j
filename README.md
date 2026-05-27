# qml4j

A pure-Java QML engine: parse `.qml` → JIT-compile to JVM bytecode (ASM) → render with Skia (Skija). Targets desktop today; Android (D8 → DEX → `InMemoryDexClassLoader`) is the next milestone.

> Status: pre-alpha. v0 + v0.1 + v0.2 ship: parser, JIT bytecode compiler, Skija renderer, anchors / Image / MouseArea / signals, Android APK with in-process D8 dexing.

## Why

Existing options have gaps:
- `paulovap/qmljava` — stalled, no renderer
- `jaqumal` / QtJambi — depend on Qt C++

qml4j aims to be a fully native-Java path from QML source to pixels.

## Architecture

```
.qml → Qml4j.parse() → AST → QmlCompiler.compile() → byte[]
                                     ↓
                          ClassLoaderBackend.defineClass()
                                     ↓
                            Item tree (QObject + Property)
                                     ↓
                            Renderer (Skija Canvas)
                                     ↓
                              SurfaceBackend
```

### Modules

| Module | Role |
|---|---|
| `qml4j-parser` | ANTLR4 grammar (`Qml.g4` + `JsExpression.g4`) → POJO AST |
| `qml4j-engine` | `QObject`, `Property<T>`, `Binding`, dependency-tracking `BindingEvaluationContext`, `DirtyQueue`, `ClassLoaderBackend` SPI |
| `qml4j-compiler` | ASM bytecode codegen — every `.qml` object → a synthetic `Component$N` class; every non-literal binding → a synthetic `Binding$M` class |
| `qml4j-render` | `Item / Rectangle / Text / Column`, Skija-based `Renderer`, `SurfaceBackend` SPI, `QmlView` |
| `qml4j-demo-desktop` | LWJGL3 + GLFW host + `GlfwSurfaceBackend` |
| `android-shell` | Separate Gradle project: APK with `DexClassLoaderBackend` (D8 → DEX → `InMemoryDexClassLoader`) and Skija GL via `GLSurfaceView` |

### Design choices

- **Runtime JIT, not source generation.** A `.qml` file becomes JVM classes inside the running process. On Android, the same byte[] will be fed through D8 → DEX → `InMemoryDexClassLoader` (API 26+).
- **Bindings are compiled methods**, not interpreters. Each binding is a generated `extends Binding` class whose `evaluate()` is real bytecode (`INVOKEVIRTUAL Property.get` + helpers in `RuntimeHelpers`).
- **Dependency tracking is automatic.** `Property.get()` registers itself with the active `BindingEvaluationContext` thread-local, so re-evaluation only needs to re-run the binding to refresh its subscription set.
- **Generated types are erased to `Object`/`Number`.** No type inference in v0; runtime helpers coerce.
- `source/target = 1.8` to stay friendly to Android dexing without desugar.

## Build

Requires JDK 8+ (built with JDK 21 toolchain), Maven 3.9+.

```sh
mvn -q verify
```

Per-module:

```sh
mvn -pl qml4j-parser   -am test
mvn -pl qml4j-engine   -am test
mvn -pl qml4j-compiler -am test
mvn -pl qml4j-render   -am test
```

## A 10-line tour

```java
import io.qml4j.engine.QmlEngine;
import io.qml4j.render.QmlView;

QmlEngine engine = new QmlEngine();
QmlView view = QmlView.withStockTypes(engine);
view.load(
    "Rectangle {\n" +
    "  width: 200; height: 100; color: \"#ff5050\"\n" +
    "  Text { x: 8; y: 8; text: \"hello qml4j\"; color: \"#ffffff\" }\n" +
    "}");
// view.renderFrame(surfaceBackend); per frame
```

What the compiler emits for `Rectangle { width: parent.width / 2 }` (sketch):

```
class Component$N extends Rectangle {
  Component$N() {
    super();
    this.width.bind(new Component$N$Binding$0(this));
  }
}
class Component$N$Binding$0 extends Binding {
  final Component$N outer;
  Object evaluate() {
    return RuntimeHelpers.div(
      RuntimeHelpers.readMember(outer.parent.get(), "width"),
      Long.valueOf(2));
  }
}
```

## v0 feature set

QML:
- Object tree with nested children
- `id:` (ignored in v0)
- Property assignments — literals, bindings
- JS expressions: arithmetic, comparison, logical, conditional, member access, unary, string concat
- `parent` member access in bindings tracks dependency reactively
- `Item / Rectangle / Text / Column` stock types

Shipped since v0:
- v0.1 — `MouseArea`, `Signal`, `on<Sig>:` handlers compiled to `Runnable` classes; assignment expressions
- v0.2 — `anchors.fill` / `anchors.centerIn` / `anchors.margins` (+ per-side); `Image` with pluggable `ResourceLoader`; `Loader` for nested QML
- v0.3 — opacity composes down the tree; `DirtyQueue` coalesces redundant binding re-evaluations per frame; `signal foo()` custom declarations on the root object
- v0.4 — `id:` resolution in bindings; signal arguments; child-object custom signals; `NumberAnimation` (target/from/to/duration/easing) ticked per frame in `QmlView.renderFrame`; `State` + `PropertyChanges` with `state:` switching (revert prior, apply next; binding expressions evaluated at apply time); `Transition { from; to; NumberAnimation }` tweens between states by spawning ephemeral animations from snapshotted before/after values; `properties: "a,b"` csv filter on `NumberAnimation`

Not yet:
- `function` / `var` / control flow statements
- Custom signal declarations on child objects (only root for now), signal arguments are parsed but unused
- `States`, `Transitions`, `Animation`, `Behavior`
- `ListView`, `Repeater`, modules, singletons, `import`
- `Qt.binding()`, `Connections`
- `anchors.left`/`right`/`top`/`bottom`/`baseline` to another item's edge (only `fill`/`centerIn` so far)

## Known limitations / tech debt

These are real and worth knowing before building on top of qml4j:

- **`id:` references unsupported in bindings.** Compiler resolves `parent.x` and context globals, but not sibling-by-id (`btn.width`). The `id:` keyword parses fine and is ignored.
- **Custom signals only on the root object.** `signal foo()` works on the root (a synthetic subclass is generated and gets a `Signal` field), but children use stock types and can't be augmented. Signal arguments are parsed but ignored at emit/handler time.
- **Skija-on-Android JNI is fragile.** Several Skija APIs crash in `NewObjectV NULL jclass` on Android because cached `jclass` refs are populated via `FindClass` in a context where the app classloader isn't visible. We currently work around `_nGetImageInfo` (parse PNG/JPG header in Java) and `Paint._nGetColor4f` (avoid `setAlphaf`). Other Skija APIs may hit the same pattern when touched — expect to add workarounds incrementally rather than fix root cause (would need a Skija patch).
- **Generated classes have no `LineNumberTable`.** Stack traces from binding evaluation point at synthetic class line 0, not back at `.qml` source lines.
- **Type system is `Object` + `Number` everywhere.** No type inference; runtime coerces on each operation. Numeric precision degrades through bind chains; string + number relies on `RuntimeHelpers.add` semantics.
- **No hot reload.** Source changes require process restart (or, for `Loader`, mutating its `source` property).
- **`Image` dimensions read from header parse, not Skia.** Animated / multi-frame formats and unusual codecs may report 0×0 even when Skia would decode them.
- **Renderer is not thread-safe.** All `render()` and `dispatchClick` calls must come from the same thread (the GL thread on Android).
- **No release-mode dexing tested.** R8 / proguard are disabled to keep Skija reflection alive; APK is debug-only for now.
- **`property` is a reserved keyword.** Grammar uses it for `property type name:` declarations, so `NumberAnimation { property: "width" }` won't parse — set the binding target name from Java (`anim.property.set("width")`) for now. Qt QML disambiguates contextually; our lexer doesn't.

## Android

The `android-shell` module is a separate Gradle project (AGP 8.5, Gradle 8.7, JDK 21).

```sh
mvn install -DskipTests   # publish qml4j-* to mavenLocal first
cd android-shell
ANDROID_HOME=$HOME/android-sdk ./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The APK ships:
- `qml4j-*` jars (transitively from mavenLocal)
- `skija-android-arm64` (the `libskija.so` is extracted from the jar into `jniLibs/arm64-v8a` at build time)
- `com.android.tools:r8:8.13.17` for in-process dexing

At runtime, `DexClassLoaderBackend.defineClasses(Map<String, byte[]>)` invokes D8 in-process to convert all generated `.class` bytes into a single dex `byte[]`, then loads them via `InMemoryDexClassLoader` (API 26+).

## Roadmap

- ~~**M7** — MouseArea + signals/slots~~ **done**
- ~~**M8** — `android-shell` with `DexClassLoaderBackend` and a HelloRectangle APK~~ **done, device-verified**
- ~~**M9** — `anchors`, `Image`, `Loader`~~ **done**
- ~~**M10** — opacity composition, dirty queue, custom signals~~ **done**
- ~~**M11** — `id:` resolution in bindings; signal arguments; child-object signals~~ **done**
- ~~**M12** — `States` / `Transitions` / `Animation` (M12a `NumberAnimation`; M12b `State`/`PropertyChanges`; M12c `Transition`)~~ **done**
- **M13** — `ListView` / `Repeater`

See `qml4j-engine/src/main/java/io/qml4j/engine/ClassLoaderBackend.java` for the SPI that decouples the JVM and Android dexing paths.

## License

TBD.
