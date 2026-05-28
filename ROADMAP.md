# qml4j roadmap

Living document. Updated whenever a milestone lands or the plan shifts.

## Where we are (2026-05-28)

Engine + compiler now cover enough QML to express a real interactive screen:
object trees, `id:` resolution, custom signals with arguments, property
bindings with dependency tracking, `States` + `Transitions` + per-property
animations (`NumberAnimation`, `Behavior`), method calls on QObjects via
reflection, user-declared `property <type> name` (root + child), and
`property alias` to forward reads/writes/dependencies to another
property.

Renderer ships `Item / Rectangle / Text / Image / MouseArea / Loader` with
anchors (`fill`, `centerIn`, edges, margins) and opacity composition.
Desktop demo (LWJGL+GL) and Android APK (D8 → DEX → `InMemoryDexClassLoader`)
both load arbitrary `.qml` at runtime.

What's **not** there yet is what this roadmap is about.

## Strategic direction

Two pressures drive the next phases:

1. **Bridge JS gap.** The expression language is rich; the statement
   language is anaemic (`var` decl, assignment, `if/else` only). Without
   functions, loops, and array/object literals the engine can't host
   logic of any complexity, and several QML controls (Repeater,
   Connections handlers) literally can't be expressed.
2. **Bridge data gap.** Everything is currently a single static object
   tree. The moment you need N items from a list (chat messages, grid
   tiles, anything) you hit a wall. Repeater + ListView are how Qt QML
   answers this; we need our version.

Phases F and G below resolve both. Phases H onward add depth in
composition, input, animation, layout — in priority order, but with
flexibility to reorder based on what the first real consumer needs.

## Phase F — JavaScript completeness (codegen-only, low risk)

Adds the missing JS surface inside expression/handler bodies. Pure
compiler work; no engine or renderer churn. Each milestone is
self-contained and small.

### M17 — function declarations
- Grammar: `function name(p1, p2) { ... }` as an `ObjectMember`, plus
  `return expr;` statement and bare `name(args)` call expressions.
- AST: `FunctionDeclaration(name, paramNames, body)` plus
  `ReturnStmt(expr)`. `CallExpr` already exists for `obj.method(args)`;
  bare-name calls reuse `CallExpr` with an `IdentifierExpr` callee.
- Compiler: each `function f(a, b)` on a QObject becomes a synthetic
  method on the component class (or child subclass). Callees resolve
  in this order: local var → signal param → declared function on
  enclosing scope → builtin method on receiver. Calls to declared
  functions emit `INVOKEVIRTUAL` on the outer/root field; calls to
  unknown bare names fail at compile time.
- Verification: handler invokes a declared function that reads/writes
  properties; recursion (`fib(n)`) works; calling a function declared
  on root from a handler nested in a child works.

### M18 — control flow in statements
- Grammar: `for (init; cond; update) stmt`, `while (cond) stmt`,
  `break;`, `continue;`. Init may be `var x = ...` or expression;
  reuse existing variable scope from M12e blocks.
- Compiler: `StatementCodegen` grows the four nodes. `break`/`continue`
  push Labels onto a small stack the loops maintain; mismatched usage
  is a compile error.
- Verification: counter loop in a handler; nested loops with
  `break`/`continue`; loop touching declared properties (the dirty
  queue should still see all touched Properties exactly once per
  handler invocation).

### M19 — array & object literals, indexing, length
- Grammar already has `ArrayLit` / `ObjectLit` nodes (per design notes
  in the original plan); wire them through codegen and add index
  access `a[i]` and string-key access `o["k"]` (member access already
  covers `o.k`).
- Compiler emits `RuntimeHelpers.makeArray(Object...)` →
  `java.util.ArrayList`, `RuntimeHelpers.makeObject(String[], Object[])`
  → `LinkedHashMap`. Index reads/writes dispatch on List vs Map at
  runtime in `RuntimeHelpers.readIndex / writeIndex`. `arr.length`
  resolves through `readMember` with a fallback that knows List/Map/
  String.
- Verification: `var xs = [1,2,3]; sum = xs[0]+xs[1]+xs[2]`; object
  literal as a config bag; `length` reads.

## Phase G — Data-driven UI

Repeater is the smallest unit that makes the framework feel real.

### M20 — Repeater (integer + JS array models)
- Render: new `Repeater extends Item`. It holds a `model` Property
  (`Number` for count, `List` for array model later) and a *delegate
  template* — captured as a `Component` reference.
- Component-as-template needs a small representation: the compiler
  detects a `Repeater { ... Item {...} }` child object, lifts the
  inner object literal into a generated `delegate factory` (a method
  on the component that takes `int index, Object modelData` and
  returns a fully wired QObject).
- Runtime: Repeater listens for `model` changes, diffs by index,
  creates/destroys delegate instances, sets each instance's `index`
  and `modelData` (declared properties on the delegate root,
  auto-injected if not present).
- Parent attachment: delegate instances reparent under the Repeater's
  *parent*, not under the Repeater itself (Qt semantics); Repeater
  acts as a controller, not a visual node.
- Verification: `Repeater { model: 5; Rectangle { width: 60; x: index*70 } }`
  produces five rectangles in a row, reactively shrinks/grows when
  `model` changes.

### M21 — ListModel
- A real observable list: `ListModel { ListElement { name: "a" } ... }`.
  Supports `count`, `append({...})`, `remove(i)`, `set(i, {...})`,
  `get(i)`. Each change emits granular notifications so Repeater can
  do minimal patches.
- Pure engine work (no codegen on Java side beyond instantiating the
  inline `ListElement` records); compiler treats `ListElement {...}`
  as a Map literal child.

### M22 — ListView (vertical, scrollable, virtualized)
- `Flickable`-like ancestor first (`contentY`, drag, fling). Then
  `ListView extends Flickable` with `model`, `delegate`, `spacing`,
  `currentIndex`. Virtualization: only instantiate delegates whose
  range overlaps the viewport ± a small overscan.
- Renderer: clip to bounds. Touch events route through `Flickable`
  before being delivered to delegates.
- Verification: 1000-item list scrolls smoothly on the device; memory
  footprint independent of model size; `currentIndex` programmatic
  changes scroll into view.

## Phase H — Composition / multi-file (sketch)

- **M23 — `Component { id: c; Item {...} }` + `Loader { sourceComponent: c }`.**
  Component is a delegate factory the user can hand around; Loader
  instantiates one. Decouples templating from Repeater so other
  consumers (dynamic dialogs, replaceable panels) can use it.
- **M24 — multi-file imports.** `import "."` brings sibling `.qml`
  files in as types; capitalised type names resolve through a
  per-document type registry. No cross-file `id:` references (deferred).
- **M25 — `Connections { target; onX: ... }`.** External signal handler
  attachment. The compiler emits a handler class bound to the target
  reference resolved at construction time; rebind on `target` change.

## Phase I — Input

- **M26 — `Keys` attached + `FocusScope`.** Key events propagate up the
  focus chain; per-item `Keys.onPressed`, `Keys.onReturnPressed`, etc.
- **M27 — `TextInput`.** Editable single-line text. Requires Skija font
  metrics + caret rendering + IME integration on Android (deferred to a
  follow-up if heavy).

## Phase J — Animation richness

- **M28 — `ColorAnimation` / `OpacityAnimation` / `RotationAnimation` /
  `PropertyAnimation`.** Specializations of the existing numeric tween;
  color needs HSV/RGB interpolation in `RuntimeHelpers`.
- **M29 — `ParallelAnimation` / `SequentialAnimation`.** Composite
  animations; Transition already does parallel implicitly, but explicit
  grouping unlocks chained scripted sequences.
- **M30 — `ScriptAction` and `PauseAnimation` inside Transitions.**

## Phase K — Layout

- **M31 — cross-item edge anchors + per-side margins.** `anchors.left:
  other.right` etc. Currently only `fill / centerIn` work cross-item;
  edge anchors compute against `AnchorLine` of any sibling/ancestor.
- **M32 — `Row` / `Grid`.** Simple positioner layouts (we ship
  `Column`).
- **M33 — `GridView`.** Like ListView but 2D.

## Cross-cutting tech debt (work on opportunistically)

| ID | Item | Why it matters |
|---|---|---|
| T1 | `LineNumberTable` in generated `.class` files mapping back to `.qml` source lines | Stack traces from binding evaluation currently point at synthetic line 0 |
| T2 | Type inference / numeric specialization in codegen | Drop universal `Object` boxing for trivially-typed bindings; faster, less GC |
| T3 | Hot reload — `QmlEngine.reload(String)` rebuilds the tree in-place | Iteration speed during demo dev |
| T4 | Audit / shim more Skija `_n*` APIs that crash on Android | `Paint.setAlphaf` and `Image.getImageInfo` already shimmed; expect more |
| T5 | Release-mode dexing with R8 + keep rules for Skija reflection | Required before any public APK release |
| T6 | Move `id:` to a real grammar production (not a normal property binding) | The current "ignored property" hack leaks into error messages |
| T7 | Disambiguate `property` keyword from a property named `property` | Blocks `NumberAnimation { property: "width" }` syntax |

## Pick-up order rationale

Phase F first because every later phase benefits: Repeater delegate
bodies want functions and array literals; Connections handlers want
real statement blocks; ListView's interaction code wants `for` loops.
Doing F first means each subsequent milestone ships in days, not
weeks.

Phase G second because it's what makes the engine "real" to a user
trying to build anything beyond a static screen.

Phase H, I, J, K are roughly independent; order them by the needs of
the first downstream consumer.

## Out of scope for now

- QML modules / singletons / versioning beyond simple multi-file
- `Qt.binding()` reflection helper, `Qt.callLater`, the `Qt` global
- QML profiler / debugger
- Layouts module (`RowLayout`, `ColumnLayout`, `GridLayout`) — the
  positioners (`Row`, `Column`, `Grid`) cover most cases
- WebView, Shapes, Particles, 3D
