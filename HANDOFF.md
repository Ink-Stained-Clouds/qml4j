# qml4j — Handoff (Android → Linux desktop)

Written 2026-06-05, handing the project from the Android/proot device to a Linux
desktop. Read this top-to-bottom once, then keep `CLAUDE.md` (house rules) and the
auto-memory index open.

## What qml4j is

A pure-Java QML engine: ANTLR parse → ASM bytecode → Skija render. North-star:
**run third-party MD3 (Material Design 3) QML component libraries unmodified.**
Development is *probe-driven* — pick a real MD3 component, write a load test, fix
engine gaps one failure at a time, ship a demo, repeat.

Current status: **12 real MD3 components run unmodified** — ScrollBar, ToolTip,
Checkbox, Switch, RadioButton, IconButton, TopAppBar, Card, FAB, Chip, Button,
Dialog. **321 tests green.** Upstream MD3 source is cloned at `/tmp/md3` (on the
old device); re-clone on the desktop (UTF-8 BOM + CRLF — strip both before parsing:
`sed '1s/^\xef\xbb\xbf//;s/\r$//'`).

## Modules

- `qml4j-parser` — ANTLR4 grammar (`Qml.g4`) + `AstBuilder`.
- `qml4j-compiler` — `QmlCompiler` (ASM bytecode), `ExpressionCodegen`, `StatementCodegen`, `TypeRegistry`.
- `qml4j-engine` — `QmlEngine`, `RuntimeHelpers`, `binding/Property` + `DirtyQueue`, `Signal`.
- `qml4j-render` — `QmlView` (load/instantiate + dispatch), `Renderer` (Skija), `items/*` (Item, Rectangle, Text, MouseArea, layouts, animations, …).
- `qml4j-demo-desktop` — **the LWJGL/GLFW desktop host** (see below). This replaces the Android APK.
- `android-shell` — **FROZEN / deprecated.** Don't build or ship it. Kept only as a reference (its `MainActivity` has a working page-launcher + GLFW-equivalent input/IME wiring worth copying to the desktop host).
- `m0-smoke` — early smoke test, ignore.

## Build & test

```
mvn install -q -DskipTests        # build all modules
mvn test                          # full suite (must be green before any commit)
mvn -pl qml4j-render test -Dtest=DialogLoadTest   # one test
```

Per-feature loop (house rule): `mvn install -DskipTests` EXIT=0 → `mvn test` green
→ commit. **Never commit on a red bar.** Commit/PR in English; chat replies in
中文 (caveman mode — terse fragments). Commit footer:
`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

## Shared showcases (NEW — single source of truth)

All QML assets now live in **`shared-qml/`** at the repo root:

- `shared-qml/md3/Core/*.qml` + `qmldir` — the bundled MD3 component library (Theme stub + the 12 components + Ripple).
- `shared-qml/showcases/*.qml` — one showcase per feature/component.

Both consumers point at it via Maven (no more copy-pasting between trees):

- `qml4j-render/pom.xml` adds `../shared-qml` as a **testResource** → tests load `/md3/Core/*` from the classpath.
- `qml4j-demo-desktop/pom.xml` adds `../shared-qml` as a **resource** → host loads `/md3/Core/*` and `/showcases/*` from the classpath.

Edit md3 components / showcases **only in `shared-qml/`**. (`android-shell/app/src/main/assets/*` still has stale copies — ignore them; that module is frozen.)

## Migration: run showcases on the LWJGL desktop host

`qml4j-demo-desktop` already has GLFW + OpenGL + Skija wired (`DesktopMain`,
`GlfwSurfaceBackend`, `AwtClipboard`) and runs `src/main/resources/demo.qml`. Run it:

```
mvn -q -pl qml4j-demo-desktop -am install -DskipTests
mvn -pl qml4j-demo-desktop exec:java
```

**Native arch:** the parent `pom.xml` is pinned to **arm64** (the old device):
`<lwjgl.natives>natives-linux-arm64</lwjgl.natives>` (line ~26) and the
`skija-linux-arm64` artifact (line ~82, also referenced in the desktop pom). On an
**x86-64 desktop**, change these to `natives-linux` and `skija-linux-x64`
respectively (Skija publishes `skija-linux-x64`; LWJGL uses `natives-linux` for
x64). On an arm64 desktop, leave as-is.

**Skija JNI gotcha (was the Android killer, should be a non-issue on desktop):**
Skija caches every jclass/jmethodID/jfieldID in native `Library._nAfterLoad()`,
which its normal `Library.load()` path calls. The Android shell bypassed that with
a manual `System.loadLibrary`, so it had to call `Library._nAfterLoad()` by hand or
every struct-returning native (measureText→Rect, getMetrics→FontMetrics, Shaper)
crashed. On desktop you depend on the Maven skija jar and let it load normally, so
this is handled — **but if you ever see "Bad address"/SIGABRT from a Skija call
that builds a Java object, that's the missing `_nAfterLoad`.**

What `DesktopMain` still needs to run the MD3 showcases (it currently can't —
`import md3.Core` won't resolve and there's no input):

1. **ResourceLoader** — `view.resources(loader)` where `loader.load("md3/Core/X.qml")`
   returns bytes via `DesktopMain.class.getResourceAsStream("/md3/Core/X.qml")`.
   Mirror Android's `AssetResourceLoader`. Without this, `import md3.Core` fails.
2. **Input** — add GLFW callbacks → `view.dispatchPointerDown/Move/Up(x,y)` (mouse
   button + cursor pos) and `view.dispatchKey(...)`. Copy the mapping from
   `android-shell/.../QmlGLSurfaceView` + `MainActivity`. Without this, ripple /
   Dialog / Button interaction can't be exercised.
3. **A launcher** — `MainActivity.showLauncher()/openPage()/pages()` is a clean
   model: a list of `(title, "showcases/XShowcase.qml")`, click loads one, Esc/Back
   returns. Reuse it (the legacy combined page ran at scale 1.0; MD3 showcases ran at
   screen density — desktop can default to 1.0 unless you add HiDPI scaling).
4. **(optional) IME/clipboard** — `AwtClipboard` already exists; text input works
   via `dispatchKey`.

Showcase roots are written to be sized by the host: each `showcases/*.qml` root has
`x:0; y:0` and **no** `width`/`height` (the host sets `view.root().width/height`
from the framebuffer size); children use `parent.width`.

## Recently-added engine capabilities (last sessions)

- **Dynamic objects:** `Component.createObject(parent[, propsMap])` + `Item.destroy()` (safe mid-tick — `tickAnimations` walks children reverse-by-index). Powers concurrent ripples and is the path for Popup/Menu/Tooltip.
- **`on<Prop>Changed` handlers** → `Property.addChangeHandler` (no-arg, Qt-style).
- **Animation `finished` signal** (`AbstractAnimation.finished`) — fires only on natural completion, not manual `stop()` (Qt finished vs stopped). Enables `onFinished`.
- **Imperative reparenting:** `item.parent = x` now moves the node between children lists (reflection in `RuntimeHelpers.writeMember`, "parent" only). Dialog.open() relies on it.
- **Plain `Text` wrapping** (`wrapMode` Wrap/WordWrap/WrapAnywhere) when width is constrained; `Item.childrenRect`; module type shadows same-named stock type (`TypeRegistry.moduleProvided`).
- **Layout fix:** a `Layout.fillWidth/Height` spacer no longer ratchets wider each pass (`LayoutSizing.mainSize` ignores a fill child's already-filled explicit size). ColumnLayout uses its own constrained width for fill + cross-align.

## Divergences in our bundled `shared-qml/md3/Core` (NOT byte-identical to upstream)

Only **`Ripple.qml`** and **`Button.qml`** diverge; everything else is unmodified.
If you re-clone `/tmp/md3`, re-apply these (they're UX fixes the user approved):

- **Ripple.qml** — full rewrite to concurrent overlapping waves: each press spawns an
  independent wave via `Component.createObject` that expands + holds at full opacity
  while pressed, then fades + self-destructs on release. (Upstream reuses one rect.)
- **Button.qml** — added `Behavior on opacity { NumberAnimation { duration: 100 } }`
  to the state layer (upstream omits it, so the colour popped on tap).

## Probe workflow (how to add the next component)

1. `cp` the component from `/tmp/md3/src/Core/Controls/X.qml` → `shared-qml/md3/Core/X.qml` (strip BOM/CRLF), add to `shared-qml/md3/Core/qmldir`.
2. Add Theme stub fields it references (colours/typography/shape) to `shared-qml/md3/Core/Theme.qml`.
3. Write `qml4j-render/src/test/java/.../XLoadTest.java` (load + assert a key property).
4. `mvn -pl qml4j-render test -Dtest=XLoadTest` → fix the first failure → repeat. Each gap is a small engine addition (a property, an enum, a signal, …).
5. Add `shared-qml/showcases/XShowcase.qml` + wire it into the desktop launcher.
6. Full `mvn test` green → commit.

Next probe candidates: Slider, Tabs, NavigationBar, Menu/Popup (uses createObject), SegmentedButton.

## Gotchas / house rules (see CLAUDE.md for the full list)

- **No fully-qualified class names inline** — always `import` (incl. tests).
- Small single-purpose methods/classes; comments explain *why*, not *what*; no emoji in source; no backward-compat shims.
- **Tooling:** one `Bash` call at a time historically (batch failures cancel siblings); use bare `grep` (NOT `rg` — `rg -r` is `--replace`, it mangled output); always Read a file before Edit; confirm `mvn install` EXIT=0 before shipping.
- Renderer unit tests **cannot run a measure/layout pass headlessly** (no Skija native on the CI/desktop-less JVM) — layout logic is tested by calling `ColumnLayout.layout()`/`RowLayout.layout()` directly with manual `implicitWidth`s (see `ColumnLayoutConstraintTest`, `RowLayoutActionsTest`). On the desktop with a real GL context this constraint is lifted; `Renderer.layoutOnly(root)` runs the settle pass without painting.

## Pointers

- Auto-memory (per-session facts/backlog): `/home/timer/.claude/projects/-home-timer-project-qml4j/memory/` — start with `MEMORY.md`, then `project_known_limitations.md` (the live gap/divergence backlog) and `project_md3_scrollbar_probe.md` (the running probe log).
- `ROADMAP.md`, `COMPAT_REPORT.md` — older planning docs (some stale; the memory backlog is more current).
