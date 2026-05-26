# qml4j

A pure-Java QML engine: parse `.qml` → JIT-compile to JVM bytecode (ASM) → render with Skia (Skija). Targets desktop today; Android (D8 → DEX → `InMemoryDexClassLoader`) is the next milestone.

> Status: pre-alpha. v0 builds and tests pass on the JVM. Desktop window not yet wired to an X display; Android shell not yet started.

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

Not yet:
- `function` / `var` / control flow statements
- Signals & slots
- `anchors`, `States`, `Transitions`, `Animation`, `Behavior`
- `ListView`, `Repeater`, modules, singletons
- `Image`, `Loader`
- `Qt.binding()`, `Connections`

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

- **M7** — MouseArea + signals/slots (handlers also compiled to methods)
- **M8** — ~~`android-shell` with `DexClassLoaderBackend` and a HelloRectangle APK~~ **done (build chain)**; device install verification still pending
- **M9** — `anchors`, `Image`, `Loader`

See `qml4j-engine/src/main/java/io/qml4j/engine/ClassLoaderBackend.java` for the SPI that decouples the JVM and Android dexing paths.

## License

TBD.
