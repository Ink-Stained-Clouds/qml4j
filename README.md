# qml4j

A pure-Java QML engine: parse `.qml` → JIT-compile the object tree to JVM bytecode (ASM) → evaluate bindings/expressions on embedded Rhino → render with Skia (Skija). Targets x86-64 desktop today; Android (D8 → DEX → `InMemoryDexClassLoader`) remains a milestone.

> Status: pre-alpha, but capable. **12+ unmodified third-party MD3 (Material Design 3) QML components run** (ScrollBar, ToolTip, Checkbox, Switch, RadioButton, IconButton, TopAppBar, Card, FAB, Chip, Button, Dialog, Slider, …). 481 tests green; checkstyle CI guard. The whole engine was refactored to polymorphic dispatch + single-responsibility modules (the long-term conventions are in `CLAUDE.md` § *Dispatch & polymorphism*).

## Why

Existing options have gaps:
- `paulovap/qmljava` — stalled, no renderer
- `jaqumal` / QtJambi — depend on Qt C++

qml4j aims to be a fully native-Java path from QML source to pixels — a drop-in engine that runs unmodified third-party QML libraries, not a hand-grown Controls clone.

## Architecture

```
.qml → Qml4j.parse() → AST → QmlCompiler.compile() → byte[]  (one Component$N per object)
                                     ↓
                          ClassLoaderBackend.defineClasses()
                                     ↓
                            Item tree (QObject + Property<T>)
                                     ↓
            bindings/handlers run as JavaScript on embedded Rhino (RhinoBinding)
            against a QmlScope; Property.get() registers reactive dependencies
                                     ↓
                            Renderer (Skija Canvas, polymorphic Item.paint)
                                     ↓
                              SurfaceBackend
```

### Modules

The four original `qml4j-{parser,engine,compiler,render}` modules were merged into a single `qml4j-core` (they remain Java *packages* inside it). The host is a separate module.

| Module | Role |
|---|---|
| `qml4j-core` | The whole engine. Packages: `parser` (ANTLR4 `Qml.g4` → POJO AST), `engine` (`QObject`, `Property<T>`, `Binding`, `DirtyQueue`, `ClassLoaderBackend` SPI, `js/` Rhino bridge), `runtime` (stateless `member`/`invoke`/`convert`/`qt` support utilities), `compiler` (ASM bytecode codegen + `emit/` member-emitter strategies), `render` (`QmlView` facade, `Renderer`, `Painter`, `items/` by feature) |
| `qml4j-demo-desktop` | LWJGL3 + GLFW host + `GlfwSurfaceBackend` + showcase launcher |
| `android-shell` | **Frozen.** Separate Gradle project: APK with `DexClassLoaderBackend` (D8 → DEX → `InMemoryDexClassLoader`). Kept for reference only; do not build. |

`shared-qml/` (repo root) is the single source of truth for the bundled MD3 component library and the showcases; both the tests and the desktop host load it from the classpath.

### Design choices

- **Runtime JIT, not source generation.** A `.qml` file becomes JVM classes inside the running process. On Android, the same `byte[]` is fed through D8 → DEX → `InMemoryDexClassLoader` (API 26+).
- **The object tree is compiled; bindings are interpreted JS.** Each QML object becomes a generated `Component$N` class wired up in its constructor. Each non-literal binding/handler is JavaScript captured from source and run by **embedded Rhino** (`RhinoBinding`) against a `QmlScope` — there is no separate JS bytecode backend (the old ASM `ExpressionCodegen`/`StatementCodegen` were removed). An unresolvable identifier in a binding is a compile error.
- **Dependency tracking is automatic.** `Property.get()` registers itself with the active `BindingEvaluationContext` thread-local, so re-evaluation only needs to re-run the binding to refresh its subscription set; `DirtyQueue` coalesces redundant re-evaluations per frame.
- **Polymorphic dispatch, not type switches.** Drawable items override `Item.paint(Painter)`; items with intrinsic size override `Item.measure(TextLayout)`; layout containers override `Item.layout()`. The compiler dispatches member emission through a `MemberEmitter` strategy map. See `CLAUDE.md` § *Dispatch & polymorphism*.
- **Generated types are erased to `Object`/`Number`.** No type inference; runtime `convert` utilities coerce.
- `source/target = 1.8` to stay friendly to Android dexing without desugar.

## Build

Requires JDK 8+ (built with a JDK 21 toolchain), Maven 3.9+.

```sh
mvn verify      # compile + 481 tests + checkstyle guard, all modules
```

```sh
mvn -pl qml4j-core test                          # engine tests only
mvn -pl qml4j-core test -Dtest=DialogLoadTest    # one test
```

The build runs offline-friendly; iteration commonly uses `mvn -o install -DskipTests` then `mvn -o -pl qml4j-core test`. A checkstyle guard (`config/checkstyle/checkstyle.xml`) is bound to `verify` and fails on unused/redundant imports and unused locals.

### Run the desktop showcases

```sh
mvn -q -pl qml4j-demo-desktop exec:java                              # launcher
mvn -q -pl qml4j-demo-desktop exec:java -Dexec.args=ButtonShowcase   # one showcase
```

Exit code 137 on close is expected (NVIDIA libEGL teardown SIGSEGV, worked around by SIGKILL-self).

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

For `Rectangle { width: parent.width / 2 }`, the compiler emits a `Component$N extends Rectangle` whose constructor binds `width` to a `RhinoBinding` carrying the source `"parent.width / 2"`; at evaluation Rhino resolves `parent` against the `QmlScope`, and reading `parent.width` registers the reactive dependency.

## Feature set

The engine hosts enough QML to run real component libraries. Supported (inventory from `StockTypes`, the compiler, and the renderer):

- Object trees, nested children, `id:` resolution in bindings (incl. forward refs via deferred bindings)
- Property declarations (`int/real/bool/string/color/var/url/alias/Item/list`), bindings + dependency tracking, grouped properties (`anchors.*`, `border.*`, `font.*`), member chains, `property alias`
- JS via Rhino: binary/unary/ternary, member/call/index, arrays, object literals, template strings, arrow functions, `function` declarations, statements (`if/for/while/return/let/var/const/block`)
- Signals (with typed args) + handlers + arrow handlers; custom `signal foo()` on root and child scopes; `on<Prop>Changed`; `Connections`
- `States` / `PropertyChanges` / `Transition`; `Behavior`; the full animation set (`Number/Color/Rotation/Opacity/Parallel/Sequential/Pause/ScriptAction`)
- `pragma Singleton`, `qmldir`, import aliases, `import "dir"`
- `Repeater` / `ListModel` / `ListElement` / `ListView` / `GridView` / `Component` / `Loader` / `Flickable`
- `Keys` attached + `FocusScope`; `Window` / `ApplicationWindow`
- `Shape` / `ShapePath` / `Path*`; layer effects (`DropShadow` / `Glow` / `ColorOverlay`) and `MultiEffect`
- `Rectangle` (radius/border/linear gradient), `Text` (font group, wrap/align/elide, icon glyphs), `Image` (fill modes), `TextInput` / `TextEdit` / `TextField` (caret/selection/clipboard via a `Clipboard` SPI), `Control` / `AbstractButton` / `Button` / `Label`
- `QtObject` + nested object properties + multi-level dotted bindings (the MD3 `Theme` pattern)
- `MouseArea` (hover: `hoverEnabled`/`containsMouse`/`entered`/`exited`; `drag.target` with axis + bounds)
- `Qt.rgba/hsla/lighter/darker/binding/callLater`, enum families (`Easing.*`, `Font.*`, `Text.*`, `Qt.Align*`)

See `ROADMAP.md` for milestone history and `COMPAT_REPORT.md` for the original Qt-parity gap analysis (largely closed).

## Known limitations / tech debt

- **C++-backed QML modules can't load.** `import md3.Core` is normally a C++-registered module; we load the `Core/*.qml` files as a directory module via `qmldir` and stub the C++ `StyleManager` in Java. We cannot load arbitrary C++ QML plugins — each target library's C++ backend must be re-implemented in Java.
- **Type system is `Object` + `Number` everywhere.** No type inference; runtime coerces on each operation. Numeric precision can degrade through long bind chains.
- **No `LineNumberTable` on generated classes.** Stack traces from binding evaluation point at synthetic classes, not `.qml` lines.
- **No hot reload.** Source changes require a process restart (or, for `Loader`, mutating its `source`).
- **`Image` dimensions read from a header parse, not Skia.** Animated/multi-frame formats may report 0×0.
- **Renderer is not thread-safe.** All `render()` / dispatch calls must come from one thread (the GL thread).
- **`property` is a reserved keyword.** `NumberAnimation { property: "width" }` won't parse — set it from Java (`anim.property.set("width")`).
- **Skija-on-Android JNI is fragile** (the `android-shell` is frozen): several `_n*` natives crash from missing cached `jclass` refs; worked around case by case.

## Android

The `android-shell` module is a frozen separate Gradle project (AGP 8.5, Gradle 8.7, JDK 21). It is kept as a reference for the desktop host's input/IME wiring and the D8 dexing path; it is not built or shipped today. At runtime its `DexClassLoaderBackend.defineClasses(Map<String, byte[]>)` invoked D8 in-process to convert generated `.class` bytes into a single dex `byte[]`, loaded via `InMemoryDexClassLoader` (API 26+).

## License

TBD.
