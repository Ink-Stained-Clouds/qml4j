# qml4j roadmap

Living document. Updated whenever a milestone lands or the plan shifts.

## Direction reset (2026-05-31, post-M50)

Phases L–R (M35–M50) all landed: data views, animation, language
closeouts, keyboard, vector graphics, layer effects, window layer, and a
first controls slice (Control/AbstractButton/Button/Label/TextField).
260 tests; every milestone shipped an on-device APK.

New primary direction: **run unmodified third-party QML component
libraries** (be a drop-in QML engine), not grow our own Controls clone.
North-star test: the MD3 library. See Phase S below and `COMPAT_REPORT.md`.
M51/M52 (more own-controls) are deferred in favour of closing the engine
gaps real libraries need.

## Where we are (2026-05-29, post-M34)

Engine + compiler now host enough QML to express interactive screens:
object trees, `id:` (with forward references via deferred bindings),
custom signals with arguments, property bindings with dependency
tracking, `States` + `Transitions` + per-property animations
(`NumberAnimation`, `Behavior`), JS statements (`if/else/for/while/
return/break/continue`, `var`), function declarations, array/object
literals, indexing + `length`, user-declared `property <type> name`,
`property alias`, signal handlers, multi-file imports via
`ResourceLoader`, `Component { ... }` + `Loader.sourceComponent`,
`Connections { target; onX: ... }`, `Repeater` with integer and JS
array models.

Renderer ships `Item / Rectangle / Text / Image / MouseArea / Loader /
Column / Row / Flickable / Timer / Gradient` plus anchors (`fill`,
`centerIn`, edges, margins), opacity composition, `rotation` / `scale`
/ `z` / `clip`, Rectangle `radius` / `border` / linear gradient.
`MouseArea.drag.target` with axis + min/max bounds. `Flickable` for
scrollable containers.

`TextInput` is the most recent push (M30–M34): focus management
(`focus` / `activeFocus`), Android IME via `BaseInputConnection`,
tap-to-caret + arrow/Home/End motion + blinking cursor, selection
range with shift+arrow / drag / replace-on-insert, clipboard
cut/copy/paste through a `Clipboard` SPI (`AndroidClipboard` +
`AwtClipboard`).

Desktop demo (LWJGL+GL) and Android APK (D8 → DEX →
`InMemoryDexClassLoader`) both load arbitrary `.qml` at runtime.
Skija android-arm64 0.143.16 has several `_n*` JNI bugs we shim
around (`Font.getMetrics`, `Paint.setAlphaf`, etc.).

## Strategic direction

TextInput is paused — composition span, touch selection handles, and
desktop key bridge are tracked separately and will resume when there
is a user need.

The next two pressures:

1. **Data-driven views.** Repeater exists but it's the wrong primitive
   for thousands of rows: no view recycling, no viewport culling,
   no scrolling integration. ListModel + ListView + GridView are how
   real QML apps render lists.
2. **Animation expressiveness.** Only `NumberAnimation` exists. Color,
   rotation, generic property tweens, plus parallel / sequential
   composition unlock real motion design.

After those, the JS gap (already mostly closed by M17–M19), the module
system, keyboard input, vector graphics, and a controls layer.

## Phase L — Data-driven views

### M35 — ListModel + ListElement
- `ListModel` extends `QObject`; not an `Item`. Holds an observable
  list of role-maps; standard methods `count`, `get(i)`, `append(obj)`,
  `insert(i, obj)`, `remove(i, n=1)`, `set(i, obj)`, `clear()`, `move`.
- `ListElement { name: "a"; ... }` inside a `ListModel` is lifted by
  the compiler into a Map literal; role names = property names.
- Granular change notifications: `rowsInserted(start, count)`,
  `rowsRemoved`, `rowsChanged`, `rowsMoved` — Signal-based, no Qt
  QAbstractItemModel reuse.
- Repeater (already shipped) gains a path that consumes a `ListModel`
  in addition to integer / JS array models, switching to incremental
  patch when granular signals are available.
- Verification: `ListModel { ListElement { name: "a" } ListElement { name: "b" } }`
  with a Repeater rendering 2 rows; `model.append({name:"c"})` adds a
  third row in place.

### M36 — ListView (vertical, virtualized)
- `ListView extends Flickable` with `model`, `delegate`, `spacing`,
  `currentIndex`, `currentItem`, `orientation` (vertical only in v0),
  `highlight`, `highlightRangeMode`, `cacheBuffer`, `header`, `footer`.
- Delegate captured as `Component`; index + modelData injected.
- Virtualization: only delegates whose y-range intersects
  `[contentY - cacheBuffer, contentY + height + cacheBuffer]` are
  instantiated; outside that range delegates are recycled (kept in a
  small free pool keyed by component identity).
- Keyboard: up/down moves `currentIndex` and scrolls into view.
- Verification: 10000-item list scrolls smoothly on device; live
  memory stays roughly constant; `positionViewAtIndex` jumps the
  viewport.

### M37 — GridView
- `GridView extends Flickable` with `cellWidth`, `cellHeight`,
  `model`, `delegate`, `currentIndex`, `flow` (LeftToRight only in v0).
- Same virtualization story as ListView, computed in 2D.
- Verification: 1000-tile photo grid scrolls; cell metrics drive
  delegate sizing automatically.

### M38 — TextEdit (multi-line)
- Most of TextInput's machinery applies: selection, clipboard, IME
  commit path, caret. Adds: line wrapping (word + WrapAnywhere),
  `wrapMode`, multi-line caret motion (up/down between visual lines),
  `verticalAlignment`, `lineCount`.
- Render: layout text into glyph runs per line via Skija (we already
  measure single-line widths); cache wrap result on `text + width +
  fontSize` change.
- Verification: paragraph wraps inside a fixed-width container; click
  in middle of line 3 lands the caret at the right index; up/down
  arrows respect visual lines.

### M39 — Image fillMode + sourceSize + asynchronous
- `Image.fillMode`: `Stretch` (current), `PreserveAspectFit`,
  `PreserveAspectCrop`, `Tile`, `TileVertically`, `TileHorizontally`,
  `Pad`.
- `Image.sourceSize` (width, height) — decode at target resolution to
  avoid full-res bitmap for tiny tiles.
- `Image.asynchronous`: decode off the render thread (single
  background executor on the engine), `status` property cycles
  `Loading → Ready / Error`.
- Verification: bg image with `PreserveAspectCrop` covers card without
  distortion; 200 thumbnail grid uses `sourceSize` to stay under
  memory budget; large image decoding doesn't drop frames.

## Phase M — Animation richness

### M40 — typed animation specializations
- `ColorAnimation` — HSV interpolation in `RuntimeHelpers`; recognises
  hex / `Qt.rgba()` / named colors.
- `RotationAnimation` with `direction: Numerical / Shortest / Clockwise
  / Counterclockwise`.
- `PropertyAnimation` as the supertype; existing `NumberAnimation`
  becomes a thin specialization.
- `OpacityAnimation` as a `PropertyAnimation { property: "opacity" }`
  alias.

### M41 — composite animations
- `ParallelAnimation { ... }` and `SequentialAnimation { ... }` as
  container animations. They expose the `Animatable` contract (start
  / stop / tick / running) and forward to children with offset
  accounting for `SequentialAnimation`.
- Verification: button "press" pulses scale + color in parallel;
  toast slides in, holds, slides out via sequential.

### M42 — `PauseAnimation` + `ScriptAction`
- `PauseAnimation { duration: 200 }` — pure delay node.
- `ScriptAction { script: "doStuff()" }` — runs a compiled JS block at
  its turn inside a sequential animation. The script's bindings
  receive the same dependency context as a handler.

## Phase N — Language gap closeouts

### M43 — `Qt.binding()` / `Qt.callLater()` / `Qt` global
- `Qt.binding(fn)` returns a `Binding` object whose `evaluate()` is
  the compiled body of `fn`; assignable into a property to
  retroactively bind. Compiler detects `Qt.binding` call at codegen
  and emits the Binding subclass plus a wrapper that returns it.
- `Qt.callLater(fn, args...)` schedules a no-arg invocation onto the
  next dirty-queue flush. Coalesces identical (target, method) pairs.
- `Qt.rgba(r,g,b,a)` / `Qt.hsla` — color factories.

### M44 — template strings + spread + arrow functions
- Lexer: backtick strings with `${expr}` interpolation.
- Grammar: `...x` in array literal / call args. Arrow function
  `(a,b) => expr` parses to the existing FunctionDeclaration node
  shape but anonymous.
- Compiler: template → string concat with `RuntimeHelpers.toStr`;
  spread → array copy at call site; arrow → synthetic method on
  enclosing class.

### M45 — pragma + module system
- `import QtQuick 2.15 as Q` — resolve to a built-in registry (we
  already auto-import stock types; this just makes the import line
  parse + lets users alias).
- `qmldir` in a directory: maps type name → file. Multi-file imports
  consult the qmldir; without one, fall back to capitalised filename
  matching (current behaviour).
- `pragma Singleton` on a `.qml` file: the engine instantiates once,
  caches by (filePath, classLoader), and references resolve to the
  cached instance.

## Phase O — Keyboard & focus

### M46 — `Keys` attached element + `FocusScope`
- `Keys.onPressed`, `Keys.onReleased`, `Keys.onSpacePressed`,
  `Keys.onReturnPressed`, etc. The compiler treats `Keys.onX: ...`
  as a handler bound to a virtual signal on the parent item;
  dispatch routes key events to focused item, then bubble up.
- `KeyEvent { key, text, modifiers, accepted }` value type passed in.
- `FocusScope` — focus boundary; tab moves focus within the scope,
  arrow keys consume there too. Renderer adds tab order computed
  from declaration order + `Item.activeFocusOnTab`.

## Phase P — Vector graphics & effects

### M47 — Path + Shape
- `Shape { ShapePath { PathLine, PathQuad, PathCubic, PathArc,
  PathMove } }`. Translate to Skija `Path` + fill/stroke paints.
- Stroke joins, caps, miter limit; fill rule winding/odd-even.
- Verification: hand-drawn checkmark glyph; pie chart with arc
  segments; arrow shapes.

### M48 — Layer effects
- `Item.layer.enabled` renders the subtree to an offscreen Skija
  `Surface`; `layer.effect` swaps the final blit shader.
- Bundled effects: `OpacityMask { source; maskSource }`,
  `DropShadow { offsetX/Y; radius; color }`,
  `Glow { radius; color }`, `ColorOverlay { color }`.
- Verification: avatar masked with circular mask; card with drop
  shadow; pressed button glows.

## Phase Q — Window layer

### M49 — `Window` + `ApplicationWindow`
- Currently the root is whatever Item the user declares. `Window`
  becomes the legitimate top-level: holds title, color, visible,
  width/height, plus an `Item` content area.
- On desktop the GLFW window honors `title` / `width` / `height` /
  `color`. Android: ignored / mapped to status bar tint where
  applicable.
- `ApplicationWindow` adds menuBar, header, footer slots (stub for
  now; controls layer fills them).

## Phase R — Controls layer (Quick.Controls subset)

### M50 — Button, Label, TextField
- `Button { text; onClicked }` — styled rectangle + Text + MouseArea.
- `Label` — Text alias with sensible defaults.
- `TextField` — TextInput inside a styled container (border + focus
  ring + placeholder).
- Style is a thin theme record; no full QtQuick.Controls style API
  yet.

### M51 — Slider, CheckBox, RadioButton, Switch  ⏳ DEFERRED
- Each composed from existing items; `value`/`checked`/`from`/`to`/`stepSize`.
- DEFERRED: superseded by Phase S below. Building more of our OWN controls
  is lower value than making the engine run THIRD-PARTY control libraries.
  Revisit only if a target library needs a primitive we lack.

### M52 — ScrollBar, ScrollIndicator, BusyIndicator, ProgressBar  ⏳ DEFERRED
- Same rationale as M51.

## Phase S — Third-party-library compatibility (NEW PRIMARY DIRECTION, 2026-05-31)

**Goal change.** The real objective (user, 2026-05-31): qml4j must run
*unmodified third-party QML component libraries*, i.e. be a drop-in QML
engine, not ship its own clone of Quick.Controls. North-star test case:
the MD3 library (github.com/sudoevolve/material-components-qml). Full gap
analysis in `COMPAT_REPORT.md` + memory `project_md3_compat_goal`. This
phase reframes M51+ : every milestone closes an *engine* capability gap
that real libraries depend on, ranked by how many components it unblocks
(frequency counts from scanning MD3's ~70 components).

Hard constraint: MD3's dynamic-color theming is a C++ backend
(`StyleManager` + Google material-color-utilities). A pure-QML engine
cannot load C++ plugins; that backend must be reimplemented in Java
(M57) or stubbed to static colors. Acceptance is therefore on the
pure-QML subset first.

Acceptance ladder: after each milestone, a hand-written minimal QML that
exercises only that feature must run on-device; once enough land, target
real MD3 components in increasing order of dependency weight.

### M51′ — QtObject + nested-object properties + multi-dot bindings  🔜 NEXT
- Register `QtObject` as a constructible base type.
- Support a property whose value is a nested object literal:
  `property QtObject color: QtObject { property color primary: "#6750A4" }`.
- Multi-level dotted read in bindings: `Theme.color.primary`,
  `_colors.onSurfaceColor` (chained member access already partly works;
  must work through a nested-QtObject-valued property + a singleton).
- Unblocks the Theme-singleton pattern — the single most common idiom in
  real libraries (MD3 Theme.qml is entirely this). Highest leverage.
- Acceptance: a `pragma Singleton QtObject` theme with nested groups,
  read via `Theme.group.prop` from another component, on-device.

### M52′ — implicitWidth/implicitHeight + font group + enums
- `implicitWidth`/`implicitHeight` as first-class Item properties:
  writable bound expressions, readable off children (drives all
  content-sized layout). Generalises the M50 Control measure hack.
- `font` grouped property on Text/controls: `font.family`,
  `font.pixelSize`, `font.weight`, `font.bold`, `font.italic`,
  `font.capitalization`; render via Skija Typeface family/weight
  (Skija already supports it). Keep flat `fontSize` as alias.
- Real enums: `Font.*`, `Text.*` (AlignVCenter/ElideRight/Wrap…),
  `Easing.*`, `Qt.Align*` — resolved as typed values, not string guesses.

### M53′ — QtQuick.Layouts
- `RowLayout` / `ColumnLayout` / `GridLayout` (+ `Flow`, `StackLayout`)
  with the `Layout.*` attached properties (fillWidth/fillHeight/
  preferredWidth/Height/alignment/margins/row/column/columnSpan).
- Pure layout logic in Java over implicitWidth (needs M52′). Used by
  ~59 MD3 files — biggest single subsystem gap.

### M54′ — Cross-cutting language gaps
- `property alias` to a child's grouped sub-property
  (`property alias font: label.font`); `default property [alias]` for
  content forwarding.
- `switch` statements in binding bodies; `required property`;
  imperative writes to grouped props (`obj.parent = x`,
  `obj.anchors.centerIn = y`); `Component.onCompleted`.
- `Qt.lighter/darker/color/point/size/rect`, `Qt.formatDate`.
- `Binding {}` element; `Connections { function onX(){} }` form;
  `import "file.js" as M` JS resources.

### M55′ — Canvas (HTML5 2D context)
- `Canvas { onPaint: { var ctx = getContext('2d'); … } }` mapping the
  2D context (beginPath/moveTo/lineTo/arc/arcTo/bezier/rect/fill/stroke/
  fillText/clip/save/restore/gradients/setLineDash/transform) onto Skija
  Canvas — near 1:1. Unblocks chart/custom-draw components (×11 in MD3).

### M56′ — QtQuick.Effects MultiEffect + hover + contentItem
- `MultiEffect` (shadowEnabled/shadowColor/shadowBlur/
  shadowVerticalOffset/shadowOpacity/blurMax/blur/brightness/
  saturation/colorization/maskSource) over Skija ImageFilter (M48 base).
- MouseArea `hoverEnabled`/`containsMouse`/`entered`/`exited` real
  dispatch (ripples & hover states depend on it).
- `contentItem` delegation + imperative reparent (needs M54′).

### M57′ — StyleManager + dynamic color (Java port)
- Java reimplementation of MD3's `StyleManager` QML singleton API
  (isDarkTheme/seedColor/currentScheme/lightScheme/darkScheme +
  setSeedColorHct/setSourceImage), backed by a Java port of
  material-color-utilities (HCT colour space, tonal palettes, schemes).
- Register it as a qml4j built-in so `import md3.Core` (loaded as a
  directory module via qmldir) finds `StyleManager`. Enables MD3 dynamic
  theming end-to-end.
- Generalises to: "to run a C++-backed QML library, reimplement its
  backend in Java and register it." Document the pattern.

### Phase S deferred / out of scope for the first pass
- TapHandler/DragHandler/HoverHandler/PointerHandler (pointer-handler
  family); 3D transforms (Rotation axis/Translate); Dialogs/Popups/Menus
  as a popup layer; XmlListModel/SqlModel; named model roles; ListView
  currentIndex/sections/highlight + key nav; IME composition; clipboard.
  Add as specific target libraries demand them.

## Cross-cutting tech debt (work on opportunistically)

| ID | Item | Why it matters |
|---|---|---|
| T1 | `LineNumberTable` in generated `.class` files mapping back to `.qml` source lines | Stack traces from binding evaluation currently point at synthetic line 0 |
| T2 | Type inference / numeric specialization in codegen | Drop universal `Object` boxing for trivially-typed bindings; faster, less GC |
| T3 | Hot reload — `QmlEngine.reload(String)` rebuilds the tree in-place | Iteration speed during demo dev |
| T4 | Audit / shim more Skija `_n*` APIs that crash on Android | `Paint.setAlphaf`, `Image.getImageInfo`, `Font.getMetrics` already shimmed; expect more |
| T5 | Release-mode dexing with R8 + keep rules for Skija reflection | Required before any public APK release |
| T6 | Move `id:` to a real grammar production (not a normal property binding) | The current "ignored property" hack leaks into error messages |
| T7 | Disambiguate `property` keyword from a property named `property` | Blocks `NumberAnimation { property: "width" }` syntax |
| T8 | TextInput: IME composition span (M32 reverted), touch selection handles, desktop key bridge | Tracked in `project_textinput_todo` memory; revisit when a real text-heavy consumer needs it |
| T9 | M50 control divergences from Qt Quick Controls (D3/D5/D7/D10/D11/D13/D15) | Tracked in `project_m50_controls` memory; only matters where a target library subclasses our controls — most libs roll their own off Item, so low priority |
| T10 | C++ QML module loading (`import Foo` registered from C++) | Unsupported by design — pure-QML/Java engine. Per-library workaround: load its QML dir via qmldir + reimplement its C++ backend in Java (see Phase S / M57′) |

## Pick-up order rationale

Phase L (data views) first because Repeater hits a wall the moment a
user has more than a handful of rows. ListModel + ListView together
make the engine usable for chat / settings / browsing UI.

Phase M (animation richness) next because tween variety is the single
biggest perceived-quality lever, and it builds on the already-stable
`Animatable` / `NumberAnimation` machinery without touching the
language.

Phases N–R are independent enough to reorder based on the first real
consumer's needs. Default order: language closeouts (M43–M45) before
keyboard (M46) before vector graphics (M47–M48) before window layer
(M49) before controls (M50–M52).

## Out of scope for now

- QML profiler / debugger
- Layouts module (`RowLayout`, `ColumnLayout`, `GridLayout`) — the
  positioners (`Row`, `Column`, `Grid`) cover most cases for v0
- WebView, Particles, 3D
- Multi-window choreography beyond a single root `Window`
- Loading C++-registered QML modules / plugins directly — unsupported by
  design; reimplement a library's C++ backend in Java instead (Phase S)

(Note: `QtQuick.Layouts`, previously out of scope, is now IN scope as
M53′ — real libraries depend on it heavily.)
