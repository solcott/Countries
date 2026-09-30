---
paths:
  - "desktop/**"
---

# `:desktop` — the Windows/Linux/macOS app

- **A plain `kotlin("jvm")` module, not multiplatform**, and it does not apply `kmp-library`. It
  declares `kotlin("test")` and the JVM toolchain itself (importing `Versions` from `build-logic`).
- **`compose.desktop.currentOs` is the one plugin-accessor dependency** — skiko's jar is classified
  by OS and arch. So everything built here, including `packageUberJarForCurrentOS`, runs on the
  **build host's OS only**; jpackage cannot cross-build.
- **`nativeDistributions { modules(...) }` is load-bearing and fails invisibly.** jpackage jlinks a
  trimmed JDK; the default set lacks `java.sql`/`jdk.unsupported` (sqlite-jdbc under the Apollo
  cache) and `java.naming`/`jdk.crypto.ec` (OkHttp TLS). `run` uses the full JDK, so a missing
  module shows up only in an *installed* build, as a crash on the first query. **Test packaging
  changes with `packageDistributionForCurrentOS`, never `run`.**
- **The window is `androidx.compose.ui.window.v2`** (`@OptIn(ExperimentalComposeUiApi::class)`),
  which is what makes `minSize` (480×600) a `Window` parameter. If the API moves before
  stabilisation, only `Main.kt`'s imports change.
- **Keyboard back is `isBackShortcut()` in `BackShortcut.kt`**, pure and tested. The backstack is
  hoisted so `onKeyEvent` can reach it, which is why `Main.kt` passes
  `circuitSaver = graph.circuitSaver`. `onRootPop` stays a no-op — Esc on the root must not quit.
- **`Window(icon = …)` is a title-bar icon, not a dock icon.** macOS takes the dock icon from the
  bundle or `java.awt.Taskbar`; `applyTaskbarIcon()` in `AppIcon.kt` sets it before the first
  window (no-op elsewhere). No task can see a dock icon: check with `:desktop:run`, and check the
  bundle icon separately with `packageDistributionForCurrentOS` — either can regress alone.
- `desktop/icons/` is the app icon source for **every** platform — see `app-icons.md`.
  `build.gradle.kts` adds it as a resource root and excludes `*.icns`/`*.ico` from the jar.
- **The hot-reload plugin is applied by id with no version and has no catalog alias** — CMP already
  puts it on the classpath, and naming a version fails the build. Nothing it adds ships.
- The bundled flag font (`src/main/resources/font/`) — see the `compose-fonts` skill before
  touching it: it must be the CBDT build and must never be handed to macOS.

The SwiftUI app is also a process named `Countries`; when driving the window by process name, target
a pid or use the hot-reload MCP tools. Running under hot reload: the `desktop-app` skill.

## Commands

```
# Desktop app
./gradlew :desktop:run
./gradlew :desktop:packageUberJarForCurrentOS      # → desktop/build/compose/jars
./gradlew :desktop:packageDistributionForCurrentOS # → desktop/build/compose/binaries

# Desktop app under Compose Hot Reload. Edits anywhere in `:ui` land in the running window in
# about a second, which is the fastest way to see a UI change on any platform here. The MCP server
# is what lets an agent look at that window — screenshots, the semantics tree, clicks and typing.
# It is wired up in `.mcp.json`, so an agent starts and stops it itself.
./gradlew :desktop:hotRun --autoReload
./gradlew :desktop:hotMcpServer
```

## Verify

`./gradlew :desktop:test`; for packaging, `./gradlew :desktop:packageDistributionForCurrentOS`.
