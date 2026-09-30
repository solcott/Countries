---
name: desktop-app
description: Running the desktop app (:desktop) under Compose Hot Reload and driving it through the hot-reload MCP server — the fastest way to see a UI change on any platform here, and the only way to screenshot or click rendered Compose UI from a terminal. Use when you need to look at the UI, check a layout breakpoint, or iterate on anything in :ui visually. The invariants for editing desktop/ itself (jlink modules, packaging, the dock icon) are in .claude/rules/desktop.md, which loads on its own.
---

# Looking at the UI through `:desktop`

Editing `desktop/` itself: `.claude/rules/desktop.md` loads automatically and carries the
invariants — packaging, `nativeDistributions`, the window v2 API, the dock icon.

## Compose Hot Reload, and the MCP server

`:desktop` applies `org.jetbrains.compose.hot-reload`. Two things about how it is wired:

- **It is applied by id with no version, and has no catalog alias.** The Compose Multiplatform
  plugin already puts `hot-reload-gradle-plugin` on the buildscript classpath — CMP 1.12.0 bundles
  1.2.0 — so naming a version fails outright with *"the plugin is already on the classpath with an
  unknown version, so compatibility cannot be checked."* Same rule as the Kotlin-family plugins.
- **Nothing it adds ships.** It contributes tasks, not dependencies; a packaged
  `Countries.app/Contents/app/` contains no hot-reload jar. `packageDistributionForCurrentOS` and
  the installed build were both re-checked after adding it.

```
./gradlew :desktop:hotRun --autoReload   # runs on an auto-provisioned JetBrains Runtime
./gradlew :desktop:hotMcpServer          # MCP server; normally started by an agent via .mcp.json
```

**Reload reaches across modules, which is the whole point** — the UI lives in `:ui`, not here.
An edit to `ui/src/commonMain/…/theme/DesktopSkin.kt` was detected, rebuilt and applied to the
running window in **672ms** warm (5s cold), with the runtime analyzer naming
`DesktopSkinKt.getDesktopSkin` and every composable downstream of it as dirty.

**Graph-shaped edits reload too.** Adding a `@Provides` to `CircuitProviders` in `:ui` — a Metro
`@ContributesTo` interface, so the change regenerates `ComposeGraph$Impl` over in `:shared-compose`
— rebuilt and reloaded without restarting the app. Note what this does *not* establish: the running
app's graph object was built by `createGraph<ComposeGraph>()` at startup, so whether a
newly-added binding is actually reachable from that already-constructed instance is untested.
Treat a *new* binding as needing a restart until proven otherwise; changing the *body* of an
existing provider is the case that plainly works.

Progress is logged to `desktop/build/run/main/main.chr.log`, not to the `hotRun` console — the
console goes quiet after startup even when reloads are landing, so read the file rather than
concluding nothing happened.

The MCP server exposes 16 tools, of which the ones that matter here are `take_screenshot`,
`get_semantic_tree`, `click`, `type_text`, `scroll_to_index`, `resize_window` and `get_ui_error`.
**This is the only way to see rendered UI in this project** — `:ui` has no Compose UI tests and no
test source set, only `@Preview`s, which Android Studio renders but nothing in a terminal does.
`resize_window` is also the cheapest way to exercise the `ListDetailPaneScaffold` breakpoint
without three separate builds.

A caution when driving anything by process name on macOS: **the SwiftUI app in `iosApp/` is also
called `Countries`**, and a stale one left running from Xcode will answer to `System Events`
queries meant for this window. Prefer the MCP tools, or target a pid.
