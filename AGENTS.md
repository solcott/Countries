# Countries

Android app that loads a list of countries from the public GraphQL API at
https://countries.trevorblades.com/ and displays them.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| GraphQL client | Apollo Kotlin |
| UI | Jetpack Compose |
| Architecture | MVI via [Circuit](https://slackhq.github.io/circuit/) |
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
an Android application module — it is the Android entry point, and the CMP iOS, desktop and web
apps get their own equivalents.

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

Compose in a migrated module comes from **two** places, and the split is not arbitrary:

| Need | Artifact | Why |
| --- | --- | --- |
| `runtime`, `runtime-saveable` | `org.jetbrains.compose.*` | Thin aliases; `androidx.compose.runtime` is already multiplatform |
| `foundation` (incl. `TextFieldState`), `ui`, `material3` | `org.jetbrains.compose.*` | **The AndroidX equivalents are Android-only** — they publish `android` plus `jvmstubs`/`linuxx64stubs`, which are not real implementations |
| `retain` | `androidx.compose.runtime:runtime-retain` | Multiplatform already, and has **no** Compose Multiplatform equivalent |
| strings, drawables | `org.jetbrains.compose.components:components-resources` | The multiplatform replacement for Android `res/` |

**material3 is on its own version line — `composeMaterial3`, not `composeMultiplatform`.** Two
plausible-looking choices are both wrong:

| CMP material3 | needs CMP core | aliases AndroidX material3 |
| --- | --- | --- |
| 1.9.0 — what the CMP plugin's own `compose.material3` accessor pins | — | **1.4.0**, a minor line *backwards* from this project |
| **1.11.0-alpha07** (chosen) | 1.11.0-beta03, resolves up to our 1.11.1 | 1.5.0-alpha13 — same line as our `material3` |
| 1.12.0-alpha03 | 1.12.0-beta01 — drags core off the 1.11 line | 1.5.0-alpha22 |

Three build requirements that are easy to miss:

- **A Compose module must apply `alias(libs.plugins.compose.multiplatform)`**, even though every
  dependency is declared by catalog coordinate rather than through `compose.*` accessors.
  `compose.foundation` pulls `compose.ui`, which on js and wasmJs depends on
  `org.jetbrains.skiko:skiko`; that plugin is what configures skiko's web packaging. Keep
  `org.jetbrains.kotlin.plugin.compose` applied alongside it — on Kotlin 2.x the CMP plugin
  expects the Compose compiler plugin to be applied separately.
- **`org.jetbrains.compose.experimental.macos.enabled=true` in `gradle.properties`.** `macosArm64`
  is in the target list and the CMP plugin refuses to configure it without this opt-in, failing at
  configuration time with "Compose targets '[macos]' are experimental".
- **A module with `composeResources` needs `android { androidResources { enable = true } }`**
  inside its `kotlin { }` block — see `ui/build.gradle.kts`. The KMP Android plugin disables
  resource processing by default, which leaves `variant.sources.assets` unavailable, and that is
  exactly where Compose Multiplatform packages resources on Android. Without it everything
  compiles, the APK simply has no `assets/composeResources/`, and the app dies on first use with
  `MissingResourceException`. Nothing warns at build time.

### Compose Multiplatform resources

Strings and drawables live in `src/commonMain/composeResources/` (`values/strings.xml`,
`drawable/*.xml`) and are reached through the generated `Res` class, not AGP's `R`:

```kotlin
import org.jetbrains.compose.resources.stringResource
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.capital

stringResource(Res.string.capital, country.capital)
painterResource(Res.drawable.home_24px)
```

`strings.xml` keeps the ordinary Android format, `%1$s` placeholders included. **Vector drawables
must contain no `?attr/…` theme attributes and no `@android:…` references** — CMP's parser cannot
resolve either, and both fail at runtime rather than at build time. Use literal colours
(`#FFFFFFFF`) and let `Icon` supply the real colour from `LocalContentColor`.

### Previews

Previews come from `org.jetbrains.compose.ui:ui-tooling-preview`, and the import is
**`androidx.compose.ui.tooling.preview.Preview`** — the AndroidX *names*, in `commonMain`. That
artifact is the Compose Multiplatform build of the AndroidX preview API: it publishes a variant for
every target this project has, and its common package is the `androidx` one, which is exactly why
Android Studio renders `commonMain` previews and why the AndroidX multipreviews
(`@PreviewScreenSizes`, `@PreviewLightDark`, `@PreviewFontScale`) and the full parameter list
(`widthDp`, `device`, `uiMode`, …) are available in common code. There is an older
`org.jetbrains.compose.ui.tooling.preview.Preview` from `components-ui-tooling-preview` — a bare
annotation with no parameters. Do not use it.

Two project multipreviews in `ui/…/PreviewSupport.kt`, so no preview repeats a device spec:

| Annotation | Use it on | Renders |
| --- | --- | --- |
| `@AppScreenPreviews` | whole screens | `@PreviewScreenSizes` (phone portrait/landscape, unfolded foldable, tablet portrait/landscape, desktop) plus a compact browser window |
| `@ComponentWidthPreviews` | a strip inside a screen | 360dp, 700dp, 1280dp |

The same file holds `PreviewSurface` (wraps in `AppTheme` + `Surface`), sample `Country` /
`Continent` / `CountryDetail` fixtures, and `loadedState` / `loadingState` / `refreshingState` /
`failedState` for building a `ContentState`. Use them rather than inventing new fixtures — the
sample list deliberately includes a country with a wrapping name and a null capital, which is what
breaks a row first.

Budget renders: the happy path gets the full size sweep, other states get `@PreviewLightDark` at
phone size. `AppTheme` reads `isSystemInDarkTheme()`, which the renderer drives from `uiMode`, so
`@PreviewLightDark` needs nothing passed to it.

`PreviewSurface` is the one composable that emits UI without a `@Preview` of its own — it *is* the
preview harness, so previewing it would be circular.

**`ui/build.gradle.kts` declares the renderer as `androidRuntimeClasspath`, not
`implementation`.** Android Studio resolves `ComposeViewAdapter` from the module's own runtime
classpath, so the annotations alone draw nothing; `androidRuntimeClasspath` is resolvable-only and
not a published variant, so the renderer is there for the IDE and never reaches `:app`. The AGP KMP
library plugin has no build types, so there is no `debugImplementation` to scope it with.

### Apollo and Kotlin Multiplatform

The Apollo Gradle plugin detects the KMP plugin by itself. It reads operations from
`src/commonMain/graphql/` and attaches the generated code to `commonMain` — no `srcDir`
or output wiring is needed in `network/build.gradle.kts`. It also adds `-lsqlite3` to
native binaries once it sees a `normalized-cache-sqlite` dependency.

`SqlNormalizedCacheFactory(name)` is an `expect` function *in Apollo*, so it compiles on
every target. What differs is where it stores data: Android uses `cacheDir` via an
`androidx.startup` initializer shipped in the AAR, the JVM uses `~/.apollo`, Apple uses
Application Support, and **js/wasmJs route to SQLDelight's SQL.js web-worker driver and
ignore the name**. The web driver needs two npm dependencies, declared on `jsMain` and
`wasmJsMain` in `network/build.gradle.kts` — pin them to the SQLDelight version that
`normalized-cache-sqlite` actually depends on (currently 2.1.0), not to the latest.

A browser **application** module additionally needs a `webpack.config.d/` entry copying
`sql.js`'s `.wasm` into the bundle — see `web/webpack.config.d/sqljs.js`. Nothing in the
Kotlin sources references that file, so without the copy step the build is clean and the
worker 404s at runtime. That is not required for a library module, so `:network` does not
have one.

**The default web driver is in-memory, so nothing survives a reload.** SQLDelight's
`@cashapp/sqldelight-sqljs-worker` does `new SQL.Database()` and never writes it anywhere;
`createDefaultWebWorkerDriver()` has no persistence story. The worker does start and does load
`sql-wasm.wasm` — verified in a browser — but on web the SQLite tier is functionally a second
memory cache behind the first one. Real persistence needs a custom worker that saves to
IndexedDB.

Per-platform Apollo client configuration goes through
`ApolloClient.Builder.platformConfiguration()`, an `expect` extension in
`network/src/commonMain`. Everything that does not vary — the endpoint, the in-memory cache
tier, the Metro provider itself — stays in `commonMain`. Add new per-platform concerns
(HTTP engines, interceptors) to that seam rather than forking the provider.

## Module structure

Nine modules, with dependencies flowing strictly downward:

```
app             → Android entry point: Activity, theme, manifest. Nothing else.
web             → Browser entry point (js + wasmJs): main(), index.html, URL routing.
shared-compose  → ComposeGraph — the Metro graph every Compose app shares
shared          → CoreGraph for non-Compose consumers, plus the root Logger
ui              → Compose UI (Circuit Ui implementations), CircuitProviders
presenter       → Circuit Screens, presenters, state, and events
repository      → domain-facing data access
network         → Apollo client, .graphql operations, generated code
model           → Kotlin domain types
```

There are **two graphs** because of how the platform apps differ:

- `shared-compose` declares `ComposeGraph`, which exposes `Circuit`. Every Compose consumer
  shares it — the Android and browser apps today, and the Compose Multiplatform iOS and desktop
  apps alongside them. None of them declares a graph of its own.
- `shared` declares `CoreGraph`, which exposes repositories and no Compose types at all. That
  is what a SwiftUI/UIKit iOS app uses: it drives Circuit `Presenter`s directly (see Circuit's
  counter sample) and needs neither a `Circuit` instance nor any `Ui.Factory`, so it must not
  link Compose.

All packages live under `io.github.solcott.countries`, with each module using its
own name as the suffix — `…countries.model`, `…countries.network`,
`…countries.repository`, `…countries.presenter`, `…countries.ui`,
`…countries.shared`, `…countries.shared.compose`, `…countries.web`. The `app`
module uses the root `io.github.solcott.countries`, which is also the
`applicationId`. Each module's Gradle `namespace` matches its package.

Rules:

- **An app module holds no dependency wiring.** `app` and `web` depend on `shared-compose`
  and nothing else from this project for the graph. Adding a `@Provides` to an app module is
  almost always wrong — it would not be available to the other platform apps.
- **The app itself is `CountriesApp` in `:ui`, not the entry point.** The theme, the backstack,
  `CircuitCompositionLocals` and `NavigableCircuitContent` live there; `MainActivity` and the
  browser `main()` each do two things only — read `circuit` off the graph, and call it. New
  screen-agnostic wiring belongs in `CountriesApp`, not in an entry point.
  `rememberCircuitNavigator`'s `onRootPop` is the exception: it is genuinely per-platform
  (Android finishes the Activity, the browser no-ops) and is passed in.
- A module contributes its own providers with `@ContributesTo(AppScope::class)`, next to the
  code they construct: `NetworkProviders` in `network`, `CircuitProviders` in `ui`,
  `LoggingProviders` in `shared`.
- `model` contains model data classes. `network`,
  `repository`, `presenter`, and `ui` all depend on it.
- **Apollo generated types never cross the `network` boundary.** `network` owns
  the mapping from generated GraphQL data classes to `model` types and returns
  only the latter. No other module imports anything from the generated package.
- `repository` is a thin pass-through to Apollo. Caching is Apollo's normalized
  cache, configured in `network` — do not add a second caching layer here.
- `Screen` definitions live in `presenter`, alongside their state and events.
- `ui` depends on `presenter` (for Screens and state types). `presenter` must
  never depend on `ui`.
- Only the graph modules (`shared`, `shared-compose`) may depend broadly across the project.

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

### The `web` module

The browser app, targeting **both** `js` and `wasmJs` from one module. Four things about it are
not obvious:

- **It does not apply `kmp-library`.** That convention is for libraries: it adds android, jvm,
  ios and macos targets, and it never calls `binaries.executable()` — which is what turns a klib
  into a webpack bundle. `web/build.gradle.kts` declares its two targets itself. It still applies
  `formatting`, and it applies `metro` so `createGraph<ComposeGraph>()` resolves, exactly as
  `:app` does.
- **`commonMain` *is* the web source set.** With only js and wasmJs on the module, the metadata
  compilation resolves against `kotlinx-browser` and `org.w3c.dom`, so `window`, `history` and
  the DOM event types are usable from common code with no `expect`/`actual` — the same thing
  Compose Multiplatform does in its own `webMain`. There is no `src/webMain` here, and adding one
  would buy nothing. `index.html` and `styles.css` live in `src/commonMain/resources/` and both
  target distributions pick them up.
- **`main()` mounts through `ComposeViewport(viewportContainerId = "composeApp")`** from
  `androidx.compose.ui.window`, which is `@ExperimentalComposeUiApi`. It waits for the DOM and,
  on wasm, for the runtime, so no `onWasmReady` wrapper is needed. The container must be sized by
  CSS — Compose measures its viewport from the element, and a zero-height container renders
  nothing with no error.
- **Browser history is hand-written**, in `BrowserHistory.kt`. Circuit ships no web history
  integration, and Compose Multiplatform's web `BackHandler` is driven by a
  `NavigationEventDispatcher` that browser `popstate` does not feed. The binding is
  bidirectional: pushes become `pushState`, in-app pops become `history.back()`, and `popstate`
  drives the backstack. `Routes.kt` owns the URL scheme — hash routes (`#/`,
  `#/country/{code}`), because a static bundle has no server to rewrite paths back to
  `index.html`.

Both web targets need **Chrome** installed to run, and `devNpm("copy-webpack-plugin")` is
declared per target because `npm()`/`devNpm()` are only available to JS-family source sets.
The js and wasm npm stores have **separate lockfiles and separate upgrade tasks** —
`kotlinUpgradeYarnLock` and `kotlinWasmUpgradeYarnLock`. Adding an npm dependency needs both.

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
- **Every composable that emits UI has a `@Preview`** — see [Previews](#previews) for the import,
  the two project multipreview annotations, and the fixtures to reuse. Preview the states that are
  easy to break, not just the happy path: loading, loaded, error, empty.

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
```

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
