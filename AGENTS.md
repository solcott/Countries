# Countries

Android app that loads a list of countries from the public GraphQL API at
https://countries.trevorblades.com/ and displays them.

## Read first

Per-platform detail lives in skills rather than here, so this file stays the part that applies to
every task. **Read the matching skill before editing, not after** — most of what they document
fails silently, so the cost of skipping one is a broken build you do not notice.

| Touching | Read |
| --- | --- |
| `iosApp/`, `apple/`, or anything Swift export | `.claude/skills/apple-app/SKILL.md` |
| the app icon, `desktop/icons/`, `Assets.xcassets` | `.claude/skills/apple-app-icons/SKILL.md` |
| `web/`, browser history, the service worker | `.claude/skills/web-app/SKILL.md` |
| `desktop/`, jpackage, the uber jar | `.claude/skills/desktop-app/SKILL.md` |
| flags, emoji, or non-Latin text rendering | `.claude/skills/compose-fonts/SKILL.md` |
| adding or changing a `@Preview` | `.claude/skills/compose-previews/SKILL.md` |
| `network/`, a `.graphql` operation, the SQL.js worker | `.claude/skills/network-apollo/SKILL.md` |
| `gradle/libs.versions.toml`, the Compose BOM, an npm dependency | `.claude/skills/dependency-bump/SKILL.md` |
| adding a Circuit screen | `.claude/skills/add-screen/SKILL.md` |
| deciding what to build or test before committing | `.claude/skills/verify/SKILL.md` |
| GitHub PR review comments, rebasing the stack | `.claude/skills/pr-review/SKILL.md` |

They are ordinary markdown — open the path directly if the skill mechanism is not available.

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
| Logging | [Kermit](https://kermit.touchlab.co/) (`co.touchlab:kermit`) |
| Formatting | ktfmt via the `com.ncorti.ktfmt.gradle` plugin |
| Testing | JUnit + Turbine |
| Build | Gradle with a version catalog (`gradle/libs.versions.toml`) |

SDK levels: `minSdk 28`, `targetSdk 37`, `compileSdk 37`.

## Kotlin Multiplatform

The project **is Kotlin Multiplatform**. The migration ran one module at a time, bottom-up:
`model` → `network` → `repository` → `presenter` → `ui` → `shared` → `shared-compose`.

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
| Web | `js`, `wasmJs` (both `browser()` only — see Testing below) |

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

The old Android-only `library.gradle.kts` convention is gone — `kmp-library` and `app` are the
only two module conventions left.

### Compose and Kotlin Multiplatform

Compose here comes from **two** places, and the split is not arbitrary: `org.jetbrains.compose.*`
supplies `runtime`, `foundation`, `ui` and `material3` — the AndroidX equivalents of the middle two
are Android-only — while `retain` comes from `androidx.compose.runtime:runtime-retain`, which has no
Compose Multiplatform equivalent. Strings and drawables come from
`org.jetbrains.compose.components:components-resources`.

**The AndroidX Compose BOM aligns the Android side and is applied to Android configurations only,
never `commonMain`; Compose Multiplatform owns everything else.** Every version pin carries its
reasoning inline in `gradle/libs.versions.toml`, next to the pin.

**Read the `dependency-bump` skill before changing any of them.** The constraints it documents fail
silently — the 1.12 floor that keeps browser fonts working, material3 being on its own version line,
the BOM never reaching `commonMain` — as do the three build requirements a Compose module has
(`alias(libs.plugins.compose.multiplatform)`, the `macos` experimental opt-in, and
`android { androidResources { enable = true } }` wherever there are `composeResources`).

### Compose Multiplatform resources

Strings and drawables live in `src/commonMain/composeResources/` (`values/strings.xml`,
`drawable/*.xml`) and are reached through the generated `Res` class, not AGP's `R`:

```kotlin
import org.jetbrains.compose.resources.stringResource
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.capital

stringResource(Res.string.capital, country.capital)
painterResource(Res.drawable.globe_24px)
```

`strings.xml` keeps the ordinary Android format, `%1$s` placeholders included. **Vector drawables
must contain no `?attr/…` theme attributes and no `@android:…` references** — CMP's parser cannot
resolve either, and both fail at runtime rather than at build time. Use literal colours
(`#FFFFFFFF`) and let `Icon` supply the real colour from `LocalContentColor`.

### Apollo and Kotlin Multiplatform

The Apollo Gradle plugin detects the KMP plugin by itself: it reads operations from
`src/commonMain/graphql/`, attaches the generated code to `commonMain`, and needs no `srcDir` or
output wiring. Per-platform client configuration goes through
`ApolloClient.Builder.platformConfiguration()`, an `expect` extension in `network/src/commonMain` —
add new per-platform concerns to that seam rather than forking the provider.

Caching is Apollo's normalized cache, configured in `network`; do not add a second layer anywhere
above it. **The web targets use a hand-written SQL.js IndexedDB worker in `network/npm/`, and
`createDefaultWebWorkerDriver()` must not come back** — the reference worker never persists
anything. That worker's `Worker` must not move up into `webMain`: the failure lands in
`compileWebMainKotlinMetadata`, blocks `assemble` for `:network` and everything above it, and
neither web target's own compile task reproduces it.

**Read the `network-apollo` skill before editing anything under `network/`.**

## Module structure

Thirteen modules, with dependencies flowing strictly downward:

```
app             → Android entry point: Activity, theme, manifest. Nothing else.
web             → Browser entry point (js + wasmJs): main(), index.html, URL routing.
desktop         → Desktop entry point (jvm): main(), Window, keyboard back, flag font.
apple           → Apple bridge (ios + macos): the Swift export for the SwiftUI app.
shared-compose  → ComposeGraph — the Metro graph every Compose app shares
shared          → CoreGraph for non-Compose consumers, plus the root Logger
ui              → Compose UI (Circuit Ui implementations), CircuitProviders
presenter       → Circuit Screens, presenters, state, and events
uistate         → ContentState and LoadStatus — view state for content from a data source
repository      → domain-facing data access
network         → Apollo client, .graphql operations, generated code
model           → Kotlin domain types: Country, CountryDetail, Language, Continent
dataresult      → DataError, Origin, Outcome — how a read went and where it came from
```

**`model` is domain nouns only.** `DataError`, `Origin` and `Outcome` describe not a thing in the
domain but the result of *reading* one, so they live in `dataresult`, which sits at the bottom with
no dependencies at all. `uistate` sits just above it and depends on nothing else.

That split is also what makes the Apple app's Swift export work, and the two reasons reinforce each
other — see the `apple-app` skill. `dataresult`, `model` and `uistate` are the
three modules exported to Swift *in full*, which is only safe because none of them contains anything
the generator chokes on. Adding a Compose type or a generic sealed type to any of the three breaks
the iOS build, and nothing else will warn you.

`applyEmission` is the one exception to `uistate` holding all of `ContentState`'s API: it takes an
`Outcome`, and lives in `presenter/ApplyEmission.kt` next to its only two callers so that `uistate`
depends on `dataresult` and nothing more.

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

All packages live under `io.github.solcott.countries`, with each module using its
own name as the suffix — `…countries.model`, `…countries.network`,
`…countries.repository`, `…countries.presenter`, `…countries.ui`,
`…countries.shared`, `…countries.shared.compose`, `…countries.web`,
`…countries.desktop`, `…countries.apple`. The `app`
module uses the root `io.github.solcott.countries`, which is also the
`applicationId`. Each module's Gradle `namespace` matches its package.

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

  The distinction from the seam above is worth keeping: a skin has no `expect`/`actual`, lives in
  no platform source set, and can be rendered from Android Studio — which is exactly why the UI is
  not forked per platform. Composables read tokens from `LocalAppSkin` rather than taking a dozen
  parameters. `AppTheme` also feeds `minInteractiveSize` into
  `LocalMinimumInteractiveComponentSize`, which is the one value that takes the whole Material
  control set from a 48dp touch target to pointer density.

  Two structural tokens matter more than the cosmetic ones: `contentMaxWidth` and `contentPanel`
  are what make `:web` read as a page rather than an app canvas, and no amount of restyling
  controls substitutes for them. `:web` also duplicates the page colour in `styles.css`, which
  paints before any Kotlin runs — **change one and change the other**, or every cold load flashes
  the wrong colour.
- A module contributes its own providers with `@ContributesTo(AppScope::class)`, next to the
  code they construct: `NetworkProviders` in `network`, `CircuitProviders` in `ui`,
  `LoggingProviders` in `shared`.
- `model` contains domain data classes and nothing else. `network`, `repository`, `presenter` and
  `ui` all depend on it. Anything describing a *read* — an error taxonomy, a cache/network origin,
  an emission — belongs in `dataresult`; anything describing *view state* belongs in `uistate`.
- **Apollo generated types never cross the `network` boundary.** `network` owns
  the mapping from generated GraphQL data classes to `model` types and returns
  only the latter. No other module imports anything from the generated package.
- `repository` is a thin pass-through to Apollo. Caching is Apollo's normalized
  cache, configured in `network` — do not add a second caching layer here.
- `Screen` definitions live in `presenter`, alongside their state and events.
- `ui` depends on `presenter` (for Screens and state types). `presenter` must
  never depend on `ui`.
- Only the graph modules (`shared`, `shared-compose`) may depend broadly across the project.

### The three non-Android entry points

`web`, `desktop` and `apple` each have a skill; the routing table at the top says which. What
follows is only the part you need to know without opening one — every item is something that
**fails with no warning**, so it is repeated here deliberately rather than left to a skill load.

- **None of the three applies `kmp-library`.** That convention is for libraries: it adds targets
  they have no use for and never calls `binaries.executable()`.
- **`:desktop`'s `nativeDistributions { modules(...) }` is load-bearing.** jpackage jlinks a
  trimmed JDK and the default set omits what sqlite-jdbc and OkHttp's TLS need. `run` uses the full
  JDK, so a missing module surfaces only in an *installed* build, as a crash on the first query.
  Test packaging changes with `packageDistributionForCurrentOS`, never `run`.
- **`:apple` — a Kotlin class must not share the exported module's name.** `CountriesKit` is
  silently renamed `CountriesKit_` in the generated Swift; the entry point is `CountriesCore` for
  that reason.
- **`:apple` — sealed types that cross to Swift are `sealed class`, not `sealed interface`.**
  Generic sealed interfaces generate Swift that does not compile, and their members are unreachable
  from another module. Nothing warns; the skill has the full table.
- **`:apple` — the deployment floor is iOS 18**, because Swift export's generated coroutine support
  uses `Synchronization.Mutex`. No documentation mentions a minimum OS.
- **The Apple app icon is generated, and an empty catalog is invisible.** An `.appiconset` whose
  `Contents.json` lists sizes but no `filename` keys builds clean, emits no warning, and produces
  an app with no icon. Verify in the built bundle, never from the build log.
- **`:web`'s offline behaviour comes from the service worker, not the Apollo cache.** The
  persistent cache only helps once the page is running; without `sw.js` an offline reload never
  fetches the bundle at all.

### Metro graph aggregation — two rules that are easy to get wrong

Both of these produce confusing errors rather than obvious ones, so they are worth knowing up
front when adding a module or a new graph:

1. **Contributions are resolved on the compile classpath of the module that declares
   `@DependencyGraph`** — Metro generates hints into `metro.hints` and locates them during graph
   supertype generation. A module added downstream, in an app module, is too late: its providers
   simply will not appear. This is the whole reason `ComposeGraph` lives in `shared-compose`
   rather than in `shared` with app modules adding `ui` themselves.
   *Symptom:* `[Metro/MissingBinding] No binding found for …`.
2. **Contributing modules must be `api`, not `implementation`, on the graph module.** Contributed
   interfaces become *supertypes* of the generated graph, so anything consuming the graph has to
   see them too.
   *Symptom:* `Cannot access '…NetworkProviders' which is a supertype of 'ComposeGraph'`.

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
  `detekt/detekt.yml` already enables `ModifierMissing` and `ModifierNotUsedAtRoot`, but detekt
  is currently only wired up for `build-logic`, so nothing enforces this in the modules yet.
- **Every composable that emits UI has a `@Preview`** — see the `compose-previews` skill for the
  import, the two project multipreview annotations, and the fixtures to reuse. Preview the states
  that are easy to break, not just the happy path: loading, loaded, error, empty.

## Build setup

`settings.gradle.kts` applies the `org.gradle.toolchains.foojay-resolver-convention`
plugin so Gradle can auto-provision missing JDKs.

The daemon JVM is pinned in the root `build.gradle.kts`:

```kotlin
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
  languageVersion = JavaLanguageVersion.of(25)
  vendor.set(JvmVendorSpec.AMAZON)
}
```

Run `./gradlew updateDaemonJvm` to regenerate `gradle/gradle-daemon-jvm.properties`
after changing that block. The generated properties file is committed.

Note that the daemon JVM is independent of what the modules compile against:
**all modules target Java 17** (`compileOptions` / Kotlin `jvmTarget`).

That 17 has one source, `Versions` in `build-logic`, and **a module build script can import it** —
`import io.github.solcott.countries.build.Versions`. It is not restricted to the convention plugins,
because `Versions.class` rides in the same `build-logic.jar` as the plugin descriptors, so applying
any convention from that build (every non-convention module applies at least `formatting`) puts it
on the script's own classpath. `:desktop` and `:apple` use it that way.

So a one-off module needing a shared version **imports it rather than earning a convention** — which
is why `kmp-library` and `app` are still the only two. Note `build-logic/build.gradle.kts`'s own
`jvmToolchain(25)` is a different fact: that is the JVM the convention plugins themselves compile
against, matching the daemon, not the modules' target.

AGP 9 has built-in Kotlin support, so Android modules must **not** apply
`org.jetbrains.kotlin.android` — AGP fails the build if they do. The root buildscript
classpath forces the KGP and Compose compiler plugin versions Metro needs; modules apply
the remaining Kotlin-family plugins (`plugin.compose`, `plugin.parcelize`) by id with no
version, picking up those classpath versions.

Metro's Circuit codegen is switched on by `metro.enableCircuitCodegen=true` in
`gradle.properties`, which generates the `Presenter.Factory` / `Ui.Factory` multibindings
from `@CircuitInject`. No separate Circuit KSP processor is needed.

`settings.gradle.kts` uses `RepositoriesMode.PREFER_SETTINGS`, not `FAIL_ON_PROJECT_REPOS`.
The Kotlin plugin unconditionally registers project-level repositories for the js/wasmJs
toolchain downloads, which `FAIL_ON_PROJECT_REPOS` rejects at registration time. Those
downloads (Node, Yarn, Binaryen) are declared as content-filtered `ivy` repositories in the
settings `repositories` block instead, so every dependency still resolves from there.

`kotlin-js-store/` holds **two** committed lockfiles, because js and wasmJs have separate npm
stores: `yarn.lock` for js and `wasm/yarn.lock` for wasmJs. Regenerate them with
`./gradlew kotlinUpgradeYarnLock` and `./gradlew kotlinWasmUpgradeYarnLock` rather than editing
them. A build that touches only one store fails with "Lock file was changed" naming the task it
needs, so it is easy to fix one and forget the other.

## Commands

```
./gradlew assembleDebug     # build the Android app
./gradlew test              # JVM unit tests
./gradlew ktfmtFormat       # apply formatting
./gradlew ktfmtCheck        # verify formatting

./gradlew :model:assemble   # build a KMP module for every target
./gradlew :model:allTests   # run a KMP module's tests on every target

# Browser app — serves on http://localhost:8080
./gradlew :web:wasmJsBrowserDevelopmentRun
./gradlew :web:jsBrowserDevelopmentRun
./gradlew :web:wasmJsBrowserDistribution   # → web/build/dist/wasmJs/productionExecutable
./gradlew :web:jsBrowserDistribution       # → web/build/dist/js/productionExecutable

# Desktop app
./gradlew :desktop:run
./gradlew :desktop:packageUberJarForCurrentOS      # → desktop/build/compose/jars
./gradlew :desktop:packageDistributionForCurrentOS # → desktop/build/compose/binaries

# Apple bridge — the Kotlin half of the SwiftUI app
./gradlew :apple:macosArm64Test :apple:iosSimulatorArm64Test

# Inspect the generated Swift without going through Xcode. Swift export registers its tasks only
# when Xcode's environment variables are present, hence the prefix. Output lands in
# apple/build/SwiftExport/<target>/Debug/files/.
CONFIGURATION=Debug SDK_NAME=macosx ARCHS=arm64 TARGET_BUILD_DIR=/tmp/se \
FRAMEWORKS_FOLDER_PATH=Frameworks ./gradlew :apple:macosArm64DebugSwiftExport

# iOS / iPadOS / macOS app. Xcode runs the Gradle export itself, so open the project and hit run
# rather than building anything first.
open iosApp/Countries.xcodeproj
xcodebuild -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
xcodebuild -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=macOS,arch=arm64' build

# Unit tests and UI tests together. Run both destinations — several UI tests are device-shape
# specific and skip themselves on the shape they do not describe.
xcodebuild test -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
xcodebuild test -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPad mini (A17 Pro),OS=18.4'
```

**Tests run on iOS simulators only; the macOS destination is build-and-run.** `xcodebuild test` for
macOS fails with "Signing for CountriesUITests requires a development team" — Xcode builds every
testable in the scheme regardless of the target's `SUPPORTED_PLATFORMS` or of `-only-testing`, and a
macOS UI-test runner cannot be ad-hoc signed. Nothing is lost: the unit tests are pure functions
with no platform-specific behaviour, and they run on the simulator.

Both desktop packaging tasks produce a build for the **host** OS only — see
the `desktop-app` skill.

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

internal fun <T, R> Flow<ApolloResponse<T>>.mapToOutcome(logger: Logger, …)
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

Tests go in `src/commonTest/kotlin` and run on **every** target — `allTests` drives six runners:
`jvmTest`, `testAndroidHostTest`, `jsBrowserTest`, `wasmJsBrowserTest`, `macosArm64Test` and
`iosSimulatorArm64Test`. `kotlin("test")` is wired into `commonTest` by the convention plugin;
add `libs.kotlinx.coroutines.test` per module if you need `runTest`.

**The web targets are `browser()` only — there is deliberately no `nodejs()`.** The web targets
exist for a browser app, and Node could not run the whole suite anyway: Compose/Molecule's frame
clock lives in Molecule's `browserMain` source set, so under Node recomposition never advances and
a presenter test awaiting a second emission fails. Adding `nodejs()` back to `kmp-library` would
reintroduce two runners that cannot pass.

Consequences worth knowing before you add the first test to a module:

- The browser runners need **Chrome** installed; the Apple runners need **Xcode**, and
  `iosSimulatorArm64Test` boots a simulator.
- Use camelCase test names, not backticked names with spaces — that is the portable choice
  across the JS and native runners.
- Adding tests can change `kotlin-js-store/yarn.lock`, because the JS test link pulls in
  packages the main compilation did not. If a build fails with "Lock file was changed", run
  `./gradlew kotlinUpgradeYarnLock` and commit the result.
- JUnit is JVM-only. Do not add `testImplementation(libs.junit)` to a migrated module; use
  `kotlin.test` assertions instead.
- **`SnapshotStateList.equals` is structural on JVM/Android but identity-based on native and
  Kotlin/JS.** Asserting `assertEquals(listOf(x), someSnapshotStateList)` passes on JVM and fails
  everywhere else. Call `.toList()` first. Expect other JVM-only accidents like this to surface
  the first time a module's tests run cross-platform.
