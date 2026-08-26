---
name: desktop-app
description: The Windows/Linux/macOS Compose app (:desktop). Use when editing anything under desktop/, changing jpackage or uber-jar packaging, touching the keyboard back shortcut, or running the app under Compose Hot Reload to look at the UI. Covers why it is a plain kotlin("jvm") module, why nativeDistributions { modules(...) } fails invisibly — a missing JDK module never shows up in `run`, only in an installed build — and why the window's minimum size now comes from the experimental window v2 API.
---

# The `desktop` module

The Windows/Linux/macOS app. It is the smallest of the three entry points, because everything that
made `:web` interesting — history, a service worker, npm — the JVM either has already or does not
need. Five things are worth knowing:

- **It is a plain `kotlin("jvm")` module, not multiplatform.** Desktop *is* the jvm target, so
  `kotlin { }` would hold exactly one target and `src/jvmMain` would be a directory with nothing to
  distinguish it from `src/main`. `:web` is multiplatform because it genuinely serves two targets
  from one module. Like `:web` it does not apply `kmp-library`, and like `:web` it declares
  `kotlin("test")` and the JVM toolchain itself, since no convention is doing it.
- **`compose.desktop.currentOs` is the one dependency declared through a plugin accessor rather
  than a catalog coordinate.** It has to be: skiko's runtime jar is classified by OS *and*
  architecture, and only the accessor picks the right one. **The consequence is that everything
  built here runs on the build host's OS only** — including `packageUberJarForCurrentOS`. Real
  cross-platform installers need the packaging task run on each OS, because jpackage cannot
  cross-build either; that is a CI matrix, and this repo has no CI yet.
- **`nativeDistributions { modules(...) }` is load-bearing and fails invisibly.** jpackage jlinks a
  trimmed JDK, and the default module set has neither `java.sql`/`jdk.unsupported` (sqlite-jdbc,
  under the Apollo cache) nor `java.naming`/`jdk.crypto.ec` (OkHttp's TLS). `run` uses the full
  JDK, so a missing module never shows up in development — only in an installed build, as a crash
  on the first query. Test packaging changes with `packageDistributionForCurrentOS`, not `run`.
- **The window comes from `androidx.compose.ui.window.v2`, not the stable window API.** That is
  what makes `minSize` a parameter of `Window` — before it, the 480×600 floor could only be reached
  through AWT, as `window.minimumSize = …` inside a `LaunchedEffect`, because the v1 `WindowState`
  could not express a minimum at all. `WindowPositionProvider.CenteredOnScreen` and
  `WindowSizeProvider.Fixed` replace `WindowPosition(Alignment.Center)` in the same swap. The cost
  is `@OptIn(ExperimentalComposeUiApi::class)` and the API's own warning that it "may be moved to
  `androidx.compose.ui.window` before stabilization" — when that happens, only `Main.kt`'s imports
  change. Verified live: the window opens at 1100×800 centered, and AWT reports
  `minimumSize=480x600`, so the parameter really does reach the peer window.
- **Keyboard back is `isBackShortcut()` in `BackShortcut.kt`**, pure and tested, for the same
  reason `historyAction()` is: a rule welded to a `KeyEvent` cannot be tested without a window. The
  backstack is hoisted out of `CountriesApp` so `Window`'s `onKeyEvent` can reach it. `onRootPop`
  is deliberately left at its default no-op — the close button is how you leave a desktop app, and
  Esc on the root screen should not quit it.

Icons live in `desktop/icons/` and are the source of truth for the app icon **on every platform**:
jpackage reads all three from disk, `icon.png` is also on the runtime classpath, and the Apple asset
catalog is derived from `icon.icns` — see `.claude/skills/apple-app-icons/SKILL.md`.
`build.gradle.kts` adds that directory as a resource root and excludes `*.icns`/`*.ico` from the
jar, since only the PNG is useful at runtime.

**`Window(icon = …)` is not a dock icon, and looks like one.** Compose Desktop's `icon` parameter
resolves to `java.awt.Window.setIconImage` — a *title bar* icon, which is what Windows and Linux
want and which macOS has no concept of. macOS takes the dock icon from the app bundle or from
`java.awt.Taskbar`, and Compose Desktop references `Taskbar` nowhere. The consequence was that a
packaged build looked right — jpackage writes `Countries.icns` and `CFBundleIconFile` into the
bundle — while `:desktop:run` and the uber jar, i.e. every development launch, showed the default
Java coffee cup. `applyTaskbarIcon()` in `AppIcon.kt` is what sets it, called from `main()` before
the first window; it no-ops off macOS, where `Feature.ICON_IMAGE` is unsupported and `Window`
already does the job.

No build task can see a dock icon, so this is a `:desktop:run`-and-look check. Test packaging
separately with `packageDistributionForCurrentOS` — the bundle icon and the runtime one come from
different mechanisms and either can regress without the other.

The flag font `:desktop` bundles is a separate concern — see `.claude/skills/compose-fonts/SKILL.md`.

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
