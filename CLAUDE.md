# qml4j — house rules

## Imports

- **Never use a fully-qualified class name inline.** Always add an `import` at the top of the file. This includes Java types (`java.util.List`), generated classes, our own modules, and one-off references in test code.
- The only exception is when two classes share a simple name and one must stay short — in that case, import one and FQN the other with a comment explaining the clash.
- Don't write `io.qml4j.engine.binding.DirtyQueue dq = ...`; write `import io.qml4j.engine.binding.DirtyQueue;` then `DirtyQueue dq = ...`.
- Same rule for tests. Don't `assertEquals` with `io.qml4j.render.items.NumberAnimation` inline — import it.

## Class & method size

Aim for small, single-purpose units. If a method or class is doing several distinct things, extract.

- **Methods** — one job. If you can't describe what a method does in one short clause without "and", split it. Prefer extracting a private helper over inlining a 30-line block.
- **Classes** — one concept. If a class accumulates unrelated state (e.g. `Item` holding rendering geometry AND state-machine orchestration AND animation pooling), pull the orchestration into its own type and inject/compose. Item holds geometry; a `StateController` or similar holds the state pipeline.
- **Constructors** that wire listeners, build maps, and configure children should hand most of that work to private methods or factories. Keep `<init>` short.
- **God methods are forbidden.** If `applyState` does diff-compute, revert, apply, snapshot, spawn animations — that's five jobs. Each gets a method.

When refactoring an existing fat class as part of new work, do the extraction in the same change. Don't pile on.

## Other code rules

- No emoji in source files unless the user asks.
- No comments restating what the code does. Comments explain *why* (a non-obvious constraint, a workaround for a known bug, a subtle invariant) or are absent.
- Don't add backward-compat shims, deprecated aliases, or "removed: ..." breadcrumbs. Delete cleanly.
- Don't add error handling, fallbacks, or defensive checks for cases that can't happen inside trusted internal code. Validate at boundaries only.
- Prefer composition over inheritance when adding orchestration. Subclass only when there's a real type relationship (Rectangle IS an Item; StateController is NOT).

## Tone

- Caveman mode is active for chat — fragments OK, drop articles/filler in conversational replies. Code stays normal.
- 中文回复用户，代码/commit/PR 用英文。
