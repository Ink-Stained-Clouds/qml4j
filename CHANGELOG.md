# Changelog

## 0.2.3

Bug fixes and a new subtree-compositing API (supersedes 0.2.2, which is identical
plus this release's `@SuppressWarnings` tidy-up).

### Compatibility
- **HarmonyOS / Android API < 33**: replaced `java.lang.ClassValue` (absent there)
  with a `ConcurrentHashMap`-backed `ClassCache` in the reflection caches, fixing a
  `NoClassDefFoundError` on first QML load.

### Rendering / layout
- **cachedLayout**: invalidate when a container's child set is rebuilt (Repeater
  re-creating delegates). Reopening the same-sized list no longer reuses stale
  geometry and collapses every row onto y=0.
- **objectName / subtree compositing** (new API): `Item.objectName`,
  `QmlView.findByObjectName(name)`, and `Renderer.renderSubtree(canvas, node, w, h)`
  let a host draw one tagged subtree in its own pass, on top of host-drawn content,
  while the rest of the scene renders underneath.

### Components
- **LinearProgress** (determinate wavy): the wave now flows and advances smoothly —
  fixes a `var`-hoisting bug that drew only the track, a `Behavior` freeze on
  per-frame values, 2px tip stepping (the active wave now ends exactly at the
  progress point), and clipped round end-caps (inset by half the stroke).
- **LoadingIndicator**: force a repaint on running/visible/completed so the morphing
  blob never comes up as just the container ring.

### Quality
- `@SuppressWarnings("unused")` on the host-facing `findByObjectName` /
  `renderSubtree` (used by the shell, invisible to single-module analysis).
