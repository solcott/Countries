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
| Multiplatform | Kotlin Multiplatform (migration in progress — see below) |
| Parcelable | [kmp-parcelize](https://github.com/solcott/kmp-parcelize) for `@Parcelize` in common code |
| Logging | [Kermit](https://kermit.touchlab.co/) (`co.touchlab:kermit`) |
| Formatting | ktfmt via the `com.ncorti.ktfmt.gradle` plugin |
| Testing | JUnit + Turbine |
| Build | Gradle with a version catalog (`gradle/libs.versions.toml`) |

SDK levels: `minSdk 28`, `targetSdk 37`, `compileSdk 37`.

## Kotlin Multiplatform

The project is **migrating to Kotlin Multiplatform**, one module at a time, bottom-up:
`model` → `network` → `repository` → `presenter` → `ui`. `app` stays an Android
application module.

Migrated so far: **`model`**, **`network`**, **`repository`**.

Supported targets, declared once in the `kmp-library` convention plugin:

| Target | Kotlin target |
| --- | --- |
| Android | `android` (via `com.android.kotlin.multiplatform.library`) |
| Desktop | `jvm` |
| iOS | `iosArm64`, `iosSimulatorArm64` |
| macOS | `macosArm64` |
| Web | `js`, `wasmJs` (both with `browser()` and `nodejs()`) |

Rules for migrated modules:

- Apply `id("kmp-library")`, never `id("library")`. Sources live in
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
  by the convention plugin, same as `library.gradle.kts` does.
- Prefer keeping code in `commonMain`. Reach for `expect`/`actual` only when a platform
  genuinely differs, not to preserve an existing Android-shaped API.
- `kmp-library` calls `applyDefaultHierarchyTemplate()`, so the intermediate source sets
  are available without any per-module wiring: **`appleMain`** (ios + macos),
  **`webMain`** (js + wasmJs), `nativeMain`. There is deliberately no android+jvm group —
  write those two actuals separately.
- **Log through Kermit, never `android.util.Log`** — it does not exist in `commonMain`. Take
  Kermit as an `implementation` dependency; a `Logger` should not appear in a module's public API.

`library.gradle.kts` is the legacy Android-only convention; it is retired once the last
module migrates.

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

A future browser **application** module will additionally need a `webpack.config.d/` entry
copying `sql.js`'s `.wasm` into the bundle. That is not required for a library module, so
`:network` does not have one.

Per-platform Apollo client configuration goes through
`ApolloClient.Builder.platformConfiguration()`, an `expect` extension in
`network/src/commonMain`. Everything that does not vary — the endpoint, the in-memory cache
tier, the Metro provider itself — stays in `commonMain`. Add new per-platform concerns
(HTTP engines, interceptors) to that seam rather than forking the provider.

## Module structure

Eight modules, with dependencies flowing strictly downward:

```
app             → Android entry point: Activity, theme, manifest. Nothing else.
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
  shares it — the Android app today, and the Compose Multiplatform iOS, desktop and web apps
  once `presenter` and `ui` are multiplatform. None of them declares a graph of its own.
- `shared` declares `CoreGraph`, which exposes repositories and no Compose types at all. That
  is what a SwiftUI/UIKit iOS app uses: it drives Circuit `Presenter`s directly (see Circuit's
  counter sample) and needs neither a `Circuit` instance nor any `Ui.Factory`, so it must not
  link Compose.

All packages live under `io.github.solcott.countries`, with each module using its
own name as the suffix — `…countries.model`, `…countries.network`,
`…countries.repository`, `…countries.presenter`, `…countries.ui`,
`…countries.shared`, `…countries.shared.compose`. The `app`
module uses the root `io.github.solcott.countries`, which is also the
`applicationId`. Each module's Gradle `namespace` matches its package.

Rules:

- **`app` holds no dependency wiring.** It depends on `shared-compose` and nothing else from
  this project. Adding a `@Provides` to an app module is almost always wrong — it would not be
  available to the other platform apps.
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

## Conventions

- Formatting is enforced by tooling, not by review. Run `./gradlew ktfmtFormat`
  before committing; CI runs `./gradlew ktfmtCheck`.
- Apollo generated code is build output. Never hand-edit it, never commit it.
  Change the `.graphql` operation files in `network` instead.
- Two screens: a country **list** (pure Compose) and a country **detail**. The
  detail screen is deliberately built with an XML layout rendered through
  `AndroidView` rather than native Compose, to demonstrate interop. Keep it that
  way — it is not an oversight to be "fixed".
- Presenters own state. Compose UI is a pure function of the Circuit state and
  emits events — no business logic, no data access.

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

`kotlin-js-store/yarn.lock` is a committed lockfile for the js/wasmJs npm dependencies.
Regenerate it with `./gradlew kotlinUpgradeYarnLock` rather than editing it.

## Commands

```
./gradlew assembleDebug     # build the Android app
./gradlew test              # JVM unit tests
./gradlew ktfmtFormat       # apply formatting
./gradlew ktfmtCheck        # verify formatting

./gradlew :model:assemble   # build a KMP module for every target
./gradlew :model:allTests   # run a KMP module's tests on every target
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

Tests go in `src/commonTest/kotlin` and run on **every** target — `:repository:allTests`
currently drives eight runners: `jvmTest`, `testAndroidHostTest`, `jsNodeTest`,
`jsBrowserTest`, `wasmJsNodeTest`, `wasmJsBrowserTest`, `macosArm64Test` and
`iosSimulatorArm64Test`. `kotlin("test")` is wired into `commonTest` by the convention plugin;
add `libs.kotlinx.coroutines.test` per module if you need `runTest`.

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
