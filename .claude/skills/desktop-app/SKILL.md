---
name: desktop-app
description: The Windows/Linux/macOS Compose app (:desktop). Use when editing anything under desktop/, changing jpackage or uber-jar packaging, or touching the keyboard back shortcut. Covers why it is a plain kotlin("jvm") module and why nativeDistributions { modules(...) } fails invisibly — a missing JDK module never shows up in `run`, only in an installed build.
---

# The `desktop` module

The Windows/Linux/macOS app. It is the smallest of the three entry points, because everything that
made `:web` interesting — history, a service worker, npm — the JVM either has already or does not
need. Four things are worth knowing:

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

