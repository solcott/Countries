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
| Formatting | ktfmt via the `com.ncorti.ktfmt.gradle` plugin |
| Testing | JUnit + Turbine |
| Build | Gradle with a version catalog (`gradle/libs.versions.toml`) |

SDK levels: `minSdk 28`, `targetSdk 37`, `compileSdk 37`.

## Kotlin Multiplatform

The project is **migrating to Kotlin Multiplatform**, one module at a time, bottom-up:
`model` → `network` → `repository` → `presenter` → `ui`. `app` stays an Android
application module.

Migrated so far: **`model`**, **`network`**.

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

Six modules, with dependencies flowing strictly downward:

```
app          → wires the Metro dependency graph, hosts the Activity
ui           → Compose UI (Circuit Ui implementations)
presenter    → Circuit Screens, presenters, state, and events
repository   → domain-facing data access
network      → Apollo client, .graphql operations, generated code
model        → Kotlin domain types
```

All packages live under `io.github.solcott.countries`, with each module using its
own name as the suffix — `…countries.model`, `…countries.network`,
`…countries.repository`, `…countries.presenter`, `…countries.ui`. The `app`
module uses the root `io.github.solcott.countries`, which is also the
`applicationId`. Each module's Gradle `namespace` matches its package.

Rules:

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
- Only `app` may depend on every other module.

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
