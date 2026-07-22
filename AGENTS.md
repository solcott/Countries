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
| Formatting | ktfmt via the `com.ncorti.ktfmt.gradle` plugin |
| Testing | JUnit + Turbine |
| Build | Gradle with a version catalog (`gradle/libs.versions.toml`) |

SDK levels: `minSdk 28`, `targetSdk 36`, `compileSdk 37`.

`compileSdk` is 37 because Circuit 0.35.x requires consumers to compile against API 37
or later; the build fails outright at 36. `targetSdk` stays at 36 — it governs runtime
behavior and is intentionally independent of `compileSdk`.

Android-only. **No Kotlin Multiplatform support** — do not add `commonMain`
source sets, KMP targets, or `expect`/`actual` declarations.

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

## Commands

```
./gradlew assembleDebug     # build
./gradlew test              # unit tests
./gradlew ktfmtFormat       # apply formatting
./gradlew ktfmtCheck        # verify formatting
```
