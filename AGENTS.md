# Countries

Android app that loads a list of countries from the public GraphQL API at
https://countries.trevorblades.com/ and displays them.

## Read first

Most of what is easy to break here fails **silently** — a clean build and a broken app. That
material is kept next to the code it governs, in two places:

**Path rules, `.claude/rules/*.md`.** Each one declares the paths it covers in `paths:` frontmatter,
and Claude Code loads it automatically when a matching file is read. Other agents: open the rule for
the directory you are editing *before* editing it.

| Touching | Rule |
| --- | --- |
| `apple/`, `iosApp/`, `model/` (exported to Swift in full) | `swift-export.md` |
| `desktop/icons/`, `AppIcon.appiconset` | `app-icons.md` |
| `web/` | `web.md` |
| `desktop/` | `desktop.md` |
| `network/` | `network.md` |
| `ui/**/*.kt` — modifiers, previews, the one platform seam | `compose-ui.md` |
| `composeResources/` | `compose-resources.md` |
| `presenter/` | `screens.md` |
| `shared/`, `shared-compose/` — the Metro graphs | `metro-graph.md` |
| `libs.versions.toml`, any build script, `build-logic/`, lockfiles | `build-scripts.md` |
| any test source set | `kmp-tests.md` |

**Skills, `.claude/skills/*/SKILL.md`**, for tasks rather than paths:

| Doing | Skill |
| --- | --- |
| adding a Circuit screen | `add-screen` |
| deciding what to build or test before committing | `verify` |
| bumping Compose, Kotlin or the BOM | `dependency-bump` |
| flags, emoji, or non-Latin text rendering wrong | `compose-fonts` |
| the reasoning behind the Swift export rules, the Apple test suites | `apple-app` |
| looking at the UI under Compose Hot Reload | `desktop-app` |
| GitHub PR review comments, rebasing the stack | `pr-review` |

Both are ordinary markdown — open the path directly if neither mechanism is available.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| GraphQL client | Apollo Kotlin |
| UI | Jetpack Compose; hand-written SwiftUI on Apple — see the `apple-app` skill |
| Architecture | MVI via [Circuit](https://slackhq.github.io/circuit/) |
| Presenters outside Compose | [Molecule](https://github.com/cashapp/molecule) — `@Composable` presenter → `StateFlow` for Swift |
| Swift interop | Kotlin **Swift export** (Alpha) — sealed types → Swift enums, `Flow` → `AsyncSequence` |
| Dependency injection | [Metro](https://zacsweers.github.io/metro/) |
| Multiplatform | Kotlin Multiplatform — every library module; `app` is the Android entry point |
| Compose (KMP) | Compose Multiplatform `foundation` + AndroidX `runtime` — see below |
| Parcelable | [kmp-parcelize](https://github.com/solcott/kmp-parcelize) for `@Parcelize` in common code |
| Screen persistence | `@CircuitSerializable` + a `SerializableCircuitSaver` — see the `add-screen` skill |
| Logging | [Kermit](https://kermit.touchlab.co/) (`co.touchlab:kermit`) |
| Formatting | ktfmt via the `com.ncorti.ktfmt.gradle` plugin |
| Testing | `kotlin.test` + Turbine (JUnit in `:app` only) |
| Build | Gradle with a version catalog (`gradle/libs.versions.toml`) |

## Kotlin Multiplatform

The project **is Kotlin Multiplatform**.

**Every library module is migrated.** The only Android-specific module left is `app`, which stays
an Android application module — it is the Android entry point. `web` and `desktop` are its
equivalents for the browser and the JVM; a CMP iOS app would be the fourth.

Supported targets, declared once in the `kmp-library` convention plugin:

| Target | Kotlin target |
| --- | --- |
| Android | `android` (via `com.android.kotlin.multiplatform.library`) |
| Desktop | `jvm` |
| iOS | `iosArm64`, `iosSimulatorArm64` |
| macOS | `macosArm64` |
| Web | `js`, `wasmJs` (both `browser()` only — see Testing KMP modules below) |

Rules for library modules:

- Apply `id("kmp-library")`. Sources live in
  `src/commonMain/kotlin`, with `src/androidMain`, `src/jvmMain`, `src/iosMain` etc.
  only for genuinely platform-specific code. Tests go in `src/commonTest/kotlin`;
  `kotlin("test")` is already wired up by the convention plugin.
- **Do not apply `com.android.library` or `org.jetbrains.kotlin.plugin.parcelize`.**
  AGP 9 dropped KMP support from `com.android.library`, and `kotlin-parcelize` does not
  work with the KMP Android plugin. Use `alias(libs.plugins.kmp.parcelize)` and import
  `@Parcelize`/`Parcelable` from `io.github.solcott.kmp.parcelize` — real
  `android.os.Parcelable` on Android, no-ops everywhere else.
- The Android target is configured in an `android { }` block *inside* `kotlin { }`,
  not a top-level `android` extension. The namespace is derived from the project name
  by the convention plugin — `shared-compose` becomes
  `io.github.solcott.countries.shared.compose`.
- Prefer keeping code in `commonMain`. Reach for `expect`/`actual` only when a platform
  genuinely differs, not to preserve an existing Android-shaped API.
- `kmp-library` calls `applyDefaultHierarchyTemplate()`, so the intermediate source sets
  are available without any per-module wiring: **`appleMain`** (ios + macos),
  **`webMain`** (js + wasmJs), `nativeMain`. There is deliberately no android+jvm group —
  write those two actuals separately.
- **Log through Kermit, never `android.util.Log`** — it does not exist in `commonMain`. Take
  Kermit as an `implementation` dependency; a `Logger` should not appear in a module's public API.

### Compose and Kotlin Multiplatform

Compose here comes from **two** places, and the split is not arbitrary: `org.jetbrains.compose.*`
supplies `runtime`, `foundation`, `ui` and `material3` — the AndroidX equivalents of the middle two
are Android-only — while `retain` comes from `androidx.compose.runtime:runtime-retain`, which has no
Compose Multiplatform equivalent. Strings and drawables come from
`org.jetbrains.compose.components:components-resources`.

**The AndroidX Compose BOM aligns the Android side and is applied to Android configurations only,
never `commonMain`; Compose Multiplatform owns everything else.** Every version pin carries its
reasoning inline in `gradle/libs.versions.toml`, next to the pin.

**`.claude/rules/build-scripts.md` loads with the catalog and every build script.** Its constraints fail
silently — the 1.12 floor that keeps browser fonts working, material3 being on its own version line,
the BOM never reaching `commonMain` — as do the three build requirements a Compose module has
(`alias(libs.plugins.compose.multiplatform)`, the `macos` experimental opt-in, and
`android { androidResources { enable = true } }` wherever there are `composeResources`).

Compose resources (`Res`, not `R`; vector drawables that fail at runtime) are in
`.claude/rules/compose-resources.md`. Apollo, the normalized cache and the SQL.js worker are in
`.claude/rules/network.md`. Caching is Apollo's normalized cache, configured in `network` — do not
add a second layer anywhere above it.

## Module structure

Eleven modules, with dependencies flowing strictly downward:

```
app             → Android entry point: Activity, theme, manifest. Nothing else.
web             → Browser entry point (js + wasmJs): main(), index.html, URL routing.
desktop         → Desktop entry point (jvm): main(), Window, keyboard back, flag font.
apple           → Apple bridge (ios + macos): the Swift export for the SwiftUI app.
shared-compose  → ComposeGraph — the Metro graph every Compose app shares
shared          → CoreGraph for non-Compose consumers, plus the root Logger
ui              → Compose UI (Circuit Ui implementations), CircuitProviders
presenter       → Circuit Screens, presenters, state, and events
repository      → domain-facing data access
network         → Apollo client, .graphql operations, generated code
model           → Kotlin domain types: Country, CountryDetail, Language, Continent
```

### The `dataresult` and `uistate` types are an external library now

`DataError`, `Origin` and `Outcome` (how a read went and where it came from) and `ContentState`,
`LoadStatus` and `applyEmission` (view state for content from a data source) used to be the
`:dataresult` and `:uistate` modules at the bottom of this build. They are now
[kmp-dataresult](https://github.com/solcott/kmp-dataresult), so `Recipes` can share them, and they
arrive as `libs.dataresult` and `libs.uistate` from GitHub Packages. Packages dropped the
`.countries.` segment: `io.github.solcott.dataresult` and `io.github.solcott.uistate`.

The ApolloResponse → `Outcome` mapping went with them, as `libs.dataresultApollo`. What is left in
`repository/Mappers.kt` is the generated-type → `model` mapping plus a thin wrapper that attaches
this project's logging policy — see Logging below.

`libs.uistateCircuit` is the fourth piece: `produceRetainedContentState` and its `Flow<P>` extension, which
collect a repository's `Outcome`s into retained `ContentState`. All three presenters use it, and
nothing here hand-rolls a `produceRetainedState` fold any more — see the `add-screen` skill.

**`model` is domain nouns only.** Anything describing a *read* rather than a thing belongs in the
library, not here.

Three consequences worth knowing:

- **Resolving them needs a token.** GitHub Packages authenticates even public reads, so a build
  needs `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` (a classic PAT with `read:packages`).
  `settings.gradle.kts` also lists `mavenLocal()` ahead of it, so `publishToMavenLocal` from the
  library repo is how to try a change before releasing it.
- **The Swift export constraints did not move with the code.** `:apple` still exports both in full,
  now by coordinate rather than by project. They remain safe to export only because neither
  contains a Compose type or a generic sealed *interface* — adding one breaks the iOS build with no
  warning. That contract now lives in another repo, so `:apple:macosArm64Test` is the only thing
  standing between a library bump and a broken iOS build. See the `apple-app` skill.
- **`Outcome` has a `Loading` case** that this project never emits. Apollo flows do not report
  their own request lifecycle, so the mapper only ever produces `Data` and `Error`; presenters
  start in a loading state and settle from there. Store5, which `Recipes` uses, does emit it. A
  `when` over `Outcome` still has to handle the case.

`model` remains one of the modules exported to Swift *in full*, alongside the two library
artifacts.

There are **two graphs** because of how the platform apps differ:

- `shared-compose` declares `ComposeGraph`, which exposes `Circuit`. Every Compose consumer
  shares it — the Android, browser and desktop apps today, and a Compose Multiplatform iOS app
  alongside them. None of them declares a graph of its own.
- `shared` declares `CoreGraph`, which exposes repositories and no Compose types at all. That is
  what the SwiftUI app uses, via `:apple`: it drives Circuit `Presenter`s directly (the shape of
  Circuit's counter sample) and needs neither a `Circuit` instance nor any `Ui.Factory`, so it
  links no Compose **UI**. It does link the Compose *runtime* and *foundation*, because that is
  what running a `@Composable` presenter under Molecule requires — see
  the `apple-app` skill.

Rules:

- **An app module holds no dependency wiring.** `app`, `web` and `desktop` depend on
  `shared-compose` and nothing else from this project for the graph. Adding a `@Provides` to an app
  module is almost always wrong — it would not be available to the other platform apps.
  `:apple` is the one exception, and only because there is no Compose UI for it to mount: something
  has to turn `CoreGraph` into an observable, and it is better done in Kotlin than in Xcode.
- **The app itself is `CountriesApp` in `:ui`, not the entry point.** The theme, the backstack,
  `CircuitCompositionLocals` and `NavigableCircuitContent` live there; `MainActivity`, the browser
  `main()` and the desktop `main()` each do two things only — read `circuit` off the graph, and
  call it. New screen-agnostic wiring belongs in `CountriesApp`, not in an entry point.
  `rememberCircuitNavigator`'s `onRootPop` is the exception: it is genuinely per-platform
  (Android finishes the Activity; the browser and desktop no-op) and is passed in. So is `skin` —
  see below.
- **`:ui` has exactly one platform seam: `LocalFlagFontFamily`.** It is null everywhere but
  desktop — see the `compose-fonts` skill. Resist adding a second; the reason this one
  earns its place is that the alternative was a wrong-looking list on two of the six platforms.
- **How a platform looks is an `AppSkin` parameter, not a seam.** `CountriesApp` takes one;
  `MaterialSkin`, `DesktopSkin` and `WebSkin` all live in `ui/…/theme/`, and an entry point does
  nothing but name the one it wants. Material 3 *is* Android's native look, so `MaterialSkin` is
  the default and Android passes nothing.

  The detail — `LocalAppSkin`, `minInteractiveSize`, the structural tokens — is in
  `.claude/rules/compose-ui.md`.
- A module contributes its own providers with `@ContributesTo(AppScope::class)`, next to the
  code they construct: `NetworkProviders` in `network`, `CircuitProviders` in `ui`,
  `LoggingProviders` in `shared`.
- `model` contains domain data classes and nothing else. `network`, `repository`, `presenter` and
  `ui` all depend on it. Anything describing a *read* — an error taxonomy, a cache/network origin,
  an emission — belongs in the `dataresult` library; anything describing *view state* belongs in
  `uistate`. Neither is a module in this build any more.
- **Apollo generated types never cross the `network` boundary.** `network` owns
  the mapping from generated GraphQL data classes to `model` types and returns
  only the latter. No other module imports anything from the generated package.
- `repository` is a thin pass-through to Apollo. Caching is Apollo's normalized
  cache, configured in `network` — do not add a second caching layer here.
- `Screen` definitions live in `presenter`, alongside their state and events. They carry
  `@CircuitSerializable(AppScope::class)`, **not** `@Parcelize` — Circuit 0.38 persists a back
  stack through a `CircuitSaver` built from generated registrations, and a screen that forgets the
  annotation compiles and then throws the first time it is saved. `@Parcelize` still applies to
  anything a presenter keeps in `rememberSaveable`, which is why `model` still uses it. Read the
  `add-screen` skill before adding a screen.
- `ui` depends on `presenter` (for Screens and state types). `presenter` must
  never depend on `ui`.
- Only the graph modules (`shared`, `shared-compose`) may depend broadly across the project.

### The non-Android entry points and the graphs

`web`, `desktop` and `apple` apply no `kmp-library` — it adds targets they have no use for and never
calls `binaries.executable()`. What each one breaks silently is in its path rule (`web.md`,
`desktop.md`, `swift-export.md`, `app-icons.md`); the two Metro aggregation rules, with the errors
they produce, are in `metro-graph.md`.

## Conventions

- Formatting is enforced by tooling, not by review. Run `./gradlew ktfmtFormat`
  before committing; CI runs `./gradlew ktfmtCheck`.
- Apollo generated code is build output. Never hand-edit it, never commit it.
  Change the `.graphql` operation files in `network` instead.
- Two screens, both pure Compose: a country **list** and a country **detail**. The detail
  screen was originally an XML layout hosted in `AndroidView`, a technical-assessment
  requirement rather than a design choice; it was converted ahead of the `ui` KMP migration,
  since `AndroidView` has no multiplatform equivalent. `ui` now has **no `android.*` imports
  at all** — keep it that way.
- Presenters own state. Compose UI is a pure function of the Circuit state and
  emits events — no business logic, no data access.
- **Every composable that emits UI takes `modifier: Modifier = Modifier`**, as the first
  optional parameter, and applies it to its **root** element — not to something nested inside.
  Composables that emit nothing are the exception: `AppTheme` (a wrapper) and
  `DataError.toUserMessage()` (returns a `String`) correctly have none.
  detekt enforces this — the `detekt` convention applies `config/detekt/detekt.yml`, with
  `ModifierMissing` and `ModifierNotUsedAtRoot` active, to every module, and CI's `./gradlew build`
  runs it.
- **Every composable that emits UI has a `@Preview`.** Nothing enforces this one; the
  `compose-conventions` subagent audits it. The import, the two project multipreviews and the
  fixtures are in `.claude/rules/compose-ui.md`. Preview the states that are easy to break, not just
  the happy path: loading, loaded, error, empty.

## Build setup

The daemon JVM is pinned in the root `build.gradle.kts`. After changing that block, run
`./gradlew updateDaemonJvm` and commit the regenerated `gradle/gradle-daemon-jvm.properties`. The
daemon JVM is independent of what the modules compile against: **all modules target Java 17**.

Metro's Circuit codegen is switched on by `metro.enableCircuitCodegen=true` in
`gradle.properties`, which generates the `Presenter.Factory` / `Ui.Factory` multibindings
from `@CircuitInject`. No separate Circuit KSP processor is needed.

Everything else about the build scripts — importing `Versions` into a module script, AGP 9's
built-in Kotlin, plugins applied by id, `RepositoriesMode.PREFER_SETTINGS`, the two npm lockfiles —
is in `.claude/rules/build-scripts.md`, which loads with any build script.

## Commands

```
./gradlew assembleDebug     # build the Android app
./gradlew test              # JVM unit tests
./gradlew ktfmtFormat       # apply formatting
./gradlew ktfmtCheck        # verify formatting

./gradlew :model:assemble   # build a KMP module for every target
./gradlew :model:allTests   # run a KMP module's tests on every target
```

Platform commands — the browser dev server and distributions, desktop packaging and hot reload,
the Swift export and `xcodebuild` invocations — are in the matching rule (`web.md`, `desktop.md`,
`swift-export.md`).

`ktfmtCheck` at the root does not cover `build-logic` — that is a separate included build.
Run it from inside `build-logic/` to check the convention plugins.

### Logging

**Never reach for `Logger` as a global, and never hold one in a file-level `private val`.** The
root `Logger` is provided by `LoggingProviders.provideLogger` in `:shared` and **injected** —
classes take it as a constructor parameter, free functions take it as a parameter:

```kotlin
internal class CountryRepositoryImpl(private val api: CountriesApi, logger: Logger) {
  private val logger = logger.withTag("CountryRepository")
}

internal fun <T, R> Flow<ApolloResponse<T>>.mapToOutcome(logger: Logger, …)   // Mappers.kt
```

Two reasons this matters:

- **Testability.** Injected loggers can be asserted on with `co.touchlab:kermit-test`'s
  `TestLogWriter` — see `MappersTest`, which pins that failures are logged with their throwable
  and that cache misses and GraphQL errors are *not*. A file-level logger makes that untestable.
- **Global configuration.** Kermit extensions (`kermit-crashlytics`, `kermit-ktor`, …) are
  configured once, on the root logger, in `:shared`. Every module that injects it picks those
  writers up automatically; a module that grabs `Logger` statically would not.

Re-tag with `withTag` per class so log output stays filterable. `TestLogWriter` and `TestConfig`
are `@ExperimentalKermitApi`, so test classes using them need
`@OptIn(ExperimentalKermitApi::class)`.

### Testing KMP modules

Tests go in `src/commonTest/kotlin` and run on **every** target — `allTests` drives six runners.
The web targets are `browser()` only, deliberately. The portability traps (camelCase names,
`SnapshotStateList`, the Android host runner's stub `Bundle`, what the first test in a Compose
module needs) are in `.claude/rules/kmp-tests.md`, which loads with any test source set.
