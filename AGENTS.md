# Countries

Android app that loads a list of countries from the public GraphQL API at
https://countries.trevorblades.com/ and displays them.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| GraphQL client | Apollo Kotlin |
| UI | Jetpack Compose; hand-written SwiftUI on Apple — see [The `apple` module](#the-apple-module) |
| Architecture | MVI via [Circuit](https://slackhq.github.io/circuit/) |
| Presenters outside Compose | [Molecule](https://github.com/cashapp/molecule) — `@Composable` presenter → `StateFlow` for Swift |
| Swift interop | [SKIE](https://skie.touchlab.co/) — sealed types → Swift enums, `Flow` → `AsyncSequence` |
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

Compose in a migrated module comes from **two** places, and the split is not arbitrary:

| Need | Artifact | Why |
| --- | --- | --- |
| `runtime`, `runtime-saveable` | `org.jetbrains.compose.*` | Thin aliases; `androidx.compose.runtime` is already multiplatform |
| `foundation` (incl. `TextFieldState`), `ui`, `material3` | `org.jetbrains.compose.*` | **The AndroidX equivalents are Android-only** — they publish `android` plus `jvmstubs`/`linuxx64stubs`, which are not real implementations |
| `retain` | `androidx.compose.runtime:runtime-retain` | Multiplatform already, and has **no** Compose Multiplatform equivalent |
| strings, drawables | `org.jetbrains.compose.components:components-resources` | The multiplatform replacement for Android `res/` |

**The project is on the Compose 1.12 line, and that is a deliberate choice made for the web
target** — 1.12 is where Compose Multiplatform gained automatic fallback-font loading in the
browser. See [Fonts on web](#fonts-on-web) below before considering a downgrade; on 1.11 every
emoji and every non-Latin script renders as tofu.

| Version ref | Value | Notes |
| --- | --- | --- |
| `composeMultiplatform` | `1.12.0-rc01` | Prerelease. The web font downloader landed in 1.12.0-alpha02 |
| `composeBom` (AndroidX) | `2026.08.00` | The BOM whose core artifacts are 1.12.0. Android configurations only |
| `composeUi` (AndroidX) | `1.12.0` | For the two coordinates the Android-only BOM cannot reach — see below |
| `composeMaterial3` (CMP) | `1.12.0-alpha03` | Wants CMP core 1.12.0-beta01, satisfied by rc01 |
| `material3` (AndroidX) | `1.5.0-alpha26` | Deliberately outside the BOM, which manages it at 1.4.0 |

**material3 is on its own version line — `composeMaterial3`, not `composeMultiplatform`.** It does
not track the core version and never has; the CMP plugin's own `compose.material3` accessor pins
something far behind, which would drag AndroidX material3 *backwards* several minor lines. Always
set `composeMaterial3` explicitly, and when changing it check two things: which CMP core version it
requires, and which AndroidX material3 it aliases.

**The AndroidX Compose BOM aligns the Android side; Compose Multiplatform owns everything else.**
That division is the whole versioning story here, and it is worth stating because the failure it
prevents is silent.

Without the BOM only `ui` and `runtime` were declared anywhere, so only they followed the `composeUi`
pin; nothing declared `foundation` or `animation`, so those drifted to whatever CMP and material3
happened to request — 1.12.0-beta01 while the rest of `:app` was on 1.12.0. Nothing warns about that.

`androidx.compose:compose-bom` is applied to **Android configurations only** — `:app`, and an
`androidMain.dependencies` block in `:ui` and `:presenter`, the only two KMP modules that pull
Compose. It must never go in `commonMain`: `androidx.compose.runtime` is genuinely multiplatform and
reaches jvm/native/web through CMP's thin alias, so a common-scoped BOM would drag those onto the
AndroidX line too.

Two details that will bite whoever bumps this next:

- **`platform(...)` does not exist on a KMP source-set dependency handler.** It is not Gradle's
  `DependencyHandler`, so an `androidMain.dependencies { }` block needs
  `project.dependencies.platform(...)`. A bare `platform(...)` fails with `Unresolved reference`.
- **material3 is deliberately outside the BOM.** The BOM manages it at 1.4.0, older than the alpha
  line this project tracks. That is harmless *because* a direct dependency with an explicit version
  beats a lower BOM constraint: `:app` resolves 1.5.0-alpha26 from the catalog and `:ui` resolves
  1.5.0-alpha22 through CMP's material3. Both resolve **up** from 1.4.0, never back. Re-check that
  after a BOM bump — if a future BOM pins material3 higher than the alpha, the BOM silently wins.

  Those two numbers differing is expected, not skew: material3 is on its own line by design, and
  `:ui` gets it via Compose Multiplatform while `:app` declares AndroidX directly.

Verify after any Compose or BOM change — both configurations, since one module is not
representative:

```
./gradlew :app:dependencies --configuration debugCompileClasspath
./gradlew :ui:dependencies  --configuration androidCompileClasspath
```

Every `androidx.compose.{ui,foundation,animation,runtime}` artifact should read 1.12.0 on both.

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

**Web uses its own SQL.js worker, and `createDefaultWebWorkerDriver()` must not come back.**
SQL.js has no storage of its own — the database is a block of memory you are responsible for
saving — and the reference worker, `@cashapp/sqldelight-sqljs-worker`, does `new SQL.Database()`
and never writes it anywhere. On that worker the SQLite tier is a second in-memory cache behind
the first one, at the cost of a 600 KB wasm blob.

`network/npm/countries-sqljs-idb-worker/` is that worker with a persistence layer: it loads the
database from IndexedDB at startup and writes `db.export()` back, debounced, after each
transaction. `NetworkProviders.web.kt` builds the `WebWorkerDriver` around it by hand.

Three things about it are easy to break:

- **It is a local npm package, not a loose `.js` file.** `new Worker(new URL(…))` has to resolve
  at bundle time, and a bare specifier out of `node_modules` is the only shape that works from a
  library module. Both `jsMain` and `wasmJsMain` declare it.
- **The `exec` response must stay `res[0] ?? { values: [] }`.** `db.exec` also returns `[]` for a
  `SELECT` that matched nothing, so returning anything richer — a rows-modified count, say —
  makes a cache miss look like a row to SQLDelight's cursor.
- **The database name travels as the worker's own name** (`new Worker(url, { name })`), because
  SQLDelight's message protocol has no field for it. It keys the IndexedDB snapshot.

**Persisting the cache is not what makes the app work offline** — see the service worker below.

Per-platform Apollo client configuration goes through
`ApolloClient.Builder.platformConfiguration()`, an `expect` extension in
`network/src/commonMain`. Everything that does not vary — the endpoint, the in-memory cache
tier, the Metro provider itself — stays in `commonMain`. Add new per-platform concerns
(HTTP engines, interceptors) to that seam rather than forking the provider.

## Module structure

Eleven modules, with dependencies flowing strictly downward:

```
app             → Android entry point: Activity, theme, manifest. Nothing else.
web             → Browser entry point (js + wasmJs): main(), index.html, URL routing.
desktop         → Desktop entry point (jvm): main(), Window, keyboard back, flag font.
apple           → Apple bridge (ios + macos): CountriesKit.xcframework for the SwiftUI app.
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
  shares it — the Android, browser and desktop apps today, and a Compose Multiplatform iOS app
  alongside them. None of them declares a graph of its own.
- `shared` declares `CoreGraph`, which exposes repositories and no Compose types at all. That is
  what the SwiftUI app uses, via `:apple`: it drives Circuit `Presenter`s directly (the shape of
  Circuit's counter sample) and needs neither a `Circuit` instance nor any `Ui.Factory`, so it
  links no Compose **UI**. It does link the Compose *runtime* and *foundation*, because that is
  what running a `@Composable` presenter under Molecule requires — see
  [The `apple` module](#the-apple-module).

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
  (Android finishes the Activity; the browser and desktop no-op) and is passed in.
- **`:ui` has exactly one platform seam: `LocalFlagFontFamily`.** It is null everywhere but
  desktop — see [Fonts on desktop](#fonts-on-desktop). Resist adding a second; the reason this one
  earns its place is that the alternative was a wrong-looking list on two of the six platforms.
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
  into a webpack bundle. `web/build.gradle.kts` declares its two targets itself — and, because
  the convention is not there to do it, wires `kotlin("test")` into `commonTest` by hand. It
  still applies
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

  **The decision itself lives in `historyAction()` (`HistoryAction.kt`), which is pure and
  tested — change the navigation rules there, not in the effect.** `BrowserHistory` only
  executes the `HistoryAction` it returns. That split exists because the rule set is a six-way
  precedence table that produced two bugs while it was welded to `window.history` and therefore
  untestable. Note especially that the first reconciliation *seeds* history from the backstack
  (`prevDepth == UNRECONCILED`): the document has one entry however deep the URL seeded the
  backstack, so a deep link needs the list synthesised underneath it.

Both web targets need **Chrome** installed to run, and `devNpm("copy-webpack-plugin")` is
declared per target because `npm()`/`devNpm()` are only available to JS-family source sets.
The js and wasm npm stores have **separate lockfiles and separate upgrade tasks** —
`kotlinUpgradeYarnLock` and `kotlinWasmUpgradeYarnLock`. Adding an npm dependency needs both.

Also add `binaries.executable()` to the `js` and `wasmJs` targets of any **Compose library**
module — see `presenter/build.gradle.kts`. CMP 1.12 added
`checkComposeUiTestConfigurationFor{Js,WasmJs}`, which hard-fails any module whose browser test
bundle reaches skiko without an executable binary to bundle it into. It fires off the target's
test task existing, not off there being test sources, and there is no opt-out property.

### The `desktop` module

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

Icons live in `desktop/icons/` and are the source of truth for both consumers: jpackage reads all
three from disk, and `icon.png` is also on the runtime classpath for the window and dock icon.
`build.gradle.kts` adds that directory as a resource root and excludes `*.icns`/`*.ico` from the
jar, since only the PNG is useful at runtime.

### The `apple` module

The Kotlin half of the SwiftUI app, packaged as `CountriesKit.xcframework` and linked by
`iosApp/Countries.xcodeproj`. One target covers iPhone, iPad and Mac — hence `:apple`, not `:ios`.

**The UI is hand-written SwiftUI and is not a port of the Compose design.** Same data, same states,
same behaviour, expressed with Apple idioms: `.searchable` rather than a text field pinned in the
list, `ContentUnavailableView` rather than `ErrorContent`, pull-to-refresh rather than a progress
strip, `Form`/`LabeledContent` rather than a column of "Label: value" text.

SwiftUI rather than UIKit because **UIKit does not run on macOS** — that is AppKit, a different
framework — and the usual escape hatch is closed: **Kotlin/Native has no Mac Catalyst target**, so a
KMP framework cannot link into a Catalyst app.

Five things worth knowing:

- **Presenters reach Swift through Molecule, not directly.** A Circuit presenter here is a
  `@Composable` function with a hidden `$composer` parameter, so Swift cannot call it at all.
  `PresenterHolders.kt` runs it with `launchMolecule(RecompositionMode.Immediate)` and exposes a
  `StateFlow`. That is why `:apple` applies the Compose compiler plugin despite rendering nothing,
  and why the framework links Compose runtime and foundation. Unlike Circuit's counter sample the
  holders do **not** wrap in `presenterOf { }` — `launchMolecule` already takes a `@Composable`
  lambda, and wrapping introduces a `@ComposableTarget("presenter")` mismatch — and they expose
  `cancel()`, which the sample omits and a repeatedly-opened detail screen needs.
- **`PresenterHolder`, the shared base, is deliberately not generic.** The scope, `cancel()` and the
  Molecule launch are shared; `state` stays declared concretely on each subclass. A
  `PresenterHolder<UiState>` would reach Swift as an Objective-C lightweight generic and make SKIE
  express `SkieSwiftStateFlow<UiState>` for a type parameter — Circuit's sample does exactly that
  and its own comments complain about the result. The Swift side *is* generic
  (`PresenterModel<Holder>`), which is fine: Swift generics are real.
- **SKIE is what makes the API usable from Swift.** It turns the sealed `DataError` and `LoadStatus`
  into exhaustively switchable Swift enums via `onEnum(of:)`, and `StateFlow` into an
  `AsyncSequence` with a non-optional `value`. Its Kotlin support range is 2.0.0–2.4.10; check it
  before bumping Kotlin. Analytics upload is turned off in `build.gradle.kts`.
- **Every type in the Swift API must be `export`ed by name, and `export` is not transitive.**
  `Screen` lives in `circuit-runtime-screen`, a different artifact from `circuit-runtime`; without
  its own `export` it reaches Swift as `Circuit_runtime_screenScreen`.
- **`linkerOpts("-lsqlite3")` is load-bearing.** The Apollo plugin adds it automatically, but only
  for `:network`'s binaries. This framework is a different binary in a module that does not apply
  Apollo, so without it the link fails on a wall of undefined `_sqlite3_*` symbols from SQLiter.
- **A Kotlin class must not share the framework's name.** `CountriesKit` would be silently renamed
  to `CountriesKit_` in Swift; the entry point is `CountriesCore` for that reason.

Navigation is Swift's. `NavigationSplitView` with a `selection: String?` is the whole navigation
state — it collapses to push-and-pop on iPhone and gives two columns on iPad and Mac. `SwiftNavigator`
only forwards `goTo`/`pop` into Swift closures and mirrors the stack back through `syncFromSwift`,
so there is exactly one writer and none of the feedback-loop guarding `BrowserHistory` needs.

Two consequences of keeping `TextFieldState` in `CountryListScreen.State`: the search box is driven
by the `SearchTextChanged` event rather than shared state, and Compose UI plus skiko end up in the
exported Obj-C header. Dropping `TextFieldState` for a plain `String` would remove both.

Xcode integration is the standard KMP "direct integration": a Run Script phase runs
`:apple:embedAndSignAppleFrameworkForXcode`, which builds only the slice Xcode is currently
targeting and embeds it. `assembleCountriesKitDebugXCFramework` builds all three slices and is for
distribution — far too slow for an edit-build-run loop. `project.pbxproj` uses a
`PBXFileSystemSynchronizedRootGroup`, so new Swift files need no project edit.

**`ContentState<T>` is a generic Objective-C class in Swift, and Swift forbids extending one in a
way that touches its type parameters.** `LoadPhase.swift` maps the non-generic `status` instead —
do not try to reinstate `isLoading`/`errorOrNull` as a `ContentState` extension.

### Testing the Apple app

Three suites, and they are deliberately different tools:

| Where | Tool | Covers |
| --- | --- | --- |
| `apple/src/commonTest` | `kotlin.test` | `SwiftNavigator`, and that Molecule turns a `@Composable` presenter into an observable `StateFlow` |
| `iosApp/CountriesTests` | Swift Testing | the pure Swift presentation logic — `userMessage(for:)`, `loadPhase`, row subtitles |
| `iosApp/CountriesUITests` | **XCTest** | the touch paths: selection, search, continent filter, back, and the iPad two-column layout |

The UI tests are XCTest rather than Swift Testing because Swift Testing has no UI-testing support —
`XCUIApplication` assertions require `XCTestCase`. That inconsistency is forced, not an oversight.

Two things to know before adding a UI test:

- **Fixtures must be at the top of the list.** `List` only realises visible rows, so a country
  further down does not exist as an accessibility element until it is scrolled to. The first draft
  of these tests used Canada and every one failed on a row that was merely off screen; they use
  Andorra and the UAE, rows one and two, via the constants in `UITestSupport.swift`.
- **`LabeledContent` merges its label and value into one element**, so the detail screen's values
  are not addressable as exact `staticTexts`. Match with a `label CONTAINS` predicate.

`NavigationUITests` branches on `UIDevice.current.userInterfaceIdiom` with `XCTSkipUnless`, so the
suite is meaningful on both destinations rather than passing vacuously on one.
`testListAndDetailAreVisibleTogetherOnIPad` is the regression test for the iPad portrait bug and has
been confirmed to fail against the pre-fix `.automatic` column visibility.

These tests hit the live GraphQL API, so a cold simulator needs network; reruns are warm from the
Apollo SQLite cache. A hermetic version would need a launch argument swapping in a stub repository,
which means production code changing shape for tests — not done.

### Fonts on desktop

Same root cause as [Fonts on web](#fonts-on-web) — Skia has no system font manager — but a
different outcome per platform, and the 1.12 web font downloader does not apply here.

| Platform | Flags | Non-Latin native names |
| --- | --- | --- |
| macOS | Apple Color Emoji, fine | fine |
| Windows | **Segoe UI Emoji has no flag glyphs** — renders as the letter pair, e.g. "FR" | fine |
| Linux | tofu without Noto Color Emoji | tofu without Noto CJK etc. |

Windows omitting flag glyphs is Microsoft's deliberate policy, not a gap that will close. So
`:desktop` bundles `NotoColorEmoji-flagsonly.ttf` and provides it through `:ui`'s
`LocalFlagFontFamily`. That covers the flags. **It does not cover the non-Latin names on Linux** —
that would mean committing several MB of Noto CJK, and a Linux desktop that renders no CJK at all
is a system that will fail on far more than this app.

The font is upstream, verbatim, so updating it is a download:

```
curl -LO https://github.com/googlefonts/noto-emoji/raw/main/fonts/NotoColorEmoji-flagsonly.ttf
```

It is SIL Open Font License 1.1; the notice is committed beside it as
`desktop/src/main/resources/font/OFL.txt`.

Two rules that `FlagFontTest` pins, both discovered the hard way:

- **It must be the CBDT build, not `Noto-COLRv1.ttf`.** COLRv1 needs FreeType 2.11+ on Linux or a
  Windows 11-era DirectWrite to rasterise, and where it is unsupported it draws *nothing* rather
  than falling back. Blank is a worse failure than letters. CBDT stores each glyph as a PNG, which
  is the most widely supported colour format there is.
- **It must not be handed to macOS.** Skia goes through CoreText there, and CoreText refuses to
  load a bitmap-only font outright — `makeFromData` returns null, and the flags would disappear on
  the one platform that never needed the font. `needsBundledFlagFont()` is the guard, and
  `flagFontFamily` is null on macOS. (The COLRv1 build is not the escape hatch: CoreText loads it
  and then Skia's CoreText scaler renders its layers as nothing.)

The practical consequence for anyone changing this: **macOS cannot verify the font renders.** The
test states the invariant as "wherever Skia can load it, it must ligate and rasterise in colour;
where it cannot, the app must not be using it", which is the strongest thing a Mac can assert.
Flags on Windows and Linux need a real machine.

### Offline, and the service worker

`web/src/commonMain/resources/sw.js`, registered from `ServiceWorker.kt`. **This is the thing that
makes the app load with no network at all.** The persistent Apollo cache only helps once the page
is running; without a service worker an offline reload never gets that far, because the bundle
itself cannot be fetched.

| Request | Strategy | Why |
| --- | --- | --- |
| Same-origin `GET` | stale-while-revalidate | The app shell, the hashed `.wasm` chunks, `composeResources` |
| `fonts.gstatic.com` | cache-first | Immutable, and what keeps flags and non-Latin text from reverting to tofu offline |
| GraphQL `POST` | network-first, cache fallback | The Cache API ignores POSTs, so responses are keyed by a hash of the request body |

**Nothing is precached.** The bundle filenames are content-hashed, so a hard-coded manifest would
rot on every build; the shell is cached as it is first requested instead. The cost is that the app
needs one online visit before it works offline.

Two practical notes:

- **Bump `CACHE_VERSION` to evict everything.** `activate` deletes every cache that is not current.
- **Under `webpack-dev-server`, stale-while-revalidate can serve one-load-stale content.** That is
  the strategy working, not a build bug — `skipWaiting()` means the next reload picks up the new
  bundle. Verify offline behaviour against a *distribution* served by a static file server, not
  against the dev server.

### Fonts on web

**Skia has no system font manager in a browser.** The browser's own fonts are unreachable from it —
Compose rasterises text into a canvas — so a codepoint with no glyph in a font Skia has been *given*
renders as tofu. Android, desktop and iOS are fine because those platforms expose a system font
fallback. This app hits it hard: flags on all 250 rows, and 53 countries whose `native` name needs
one of 14 non-Latin scripts.

Compose Multiplatform **1.12** solves it. `ComposeWindow` calls `installFallbackFontDownloader()`
unconditionally on web; Skia reports unresolved codepoints during layout, the downloader batches
them, fetches the matching Noto woff2 subsets from `https://fonts.gstatic.com/s/`, preloads them,
and calls `onNewFontInstalled()` to force a re-layout. Noto Color Emoji is in that table — chunk 0
is exactly the flag block `U+1f1e6-1f1ff` — and the CJK variant is picked from
`navigator.language`.

So:

- **Do not bundle fallback fonts, and do not hand-roll `FontFamily.Resolver.preload`.** That is the
  documented approach for 1.11 and earlier, and it is obsolete here. It would mean committing
  megabytes of woff2 and rebuilding the resolver on every late font arrival (`FontCache` is
  per-resolver, so a fresh one has to be re-preloaded with the whole accumulated set).
- **The downloader is unconditional — there is no property to turn it off.** The web app therefore
  makes runtime requests to a Google CDN, and non-Latin text and flags will not render offline
  until the browser has cached those files.
- **Do not drop `composeMultiplatform` below 1.12.** It reintroduces the bug silently: everything
  builds, and only a human looking at the running page notices.

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

# Desktop app
./gradlew :desktop:run
./gradlew :desktop:packageUberJarForCurrentOS      # → desktop/build/compose/jars
./gradlew :desktop:packageDistributionForCurrentOS # → desktop/build/compose/binaries

# Apple bridge — the Kotlin half of the SwiftUI app
./gradlew :apple:macosArm64Test :apple:iosSimulatorArm64Test
./gradlew :apple:assembleCountriesKitDebugXCFramework   # → apple/build/XCFrameworks/debug

# iOS / iPadOS / macOS app. Xcode runs the Gradle framework build itself, so open the project
# and hit run rather than building the framework first.
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
[The `desktop` module](#the-desktop-module).

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
