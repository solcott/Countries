---
paths:
  - "gradle/libs.versions.toml"
  - "**/build.gradle.kts"
  - "settings.gradle.kts"
  - "gradle.properties"
  - "build-logic/**"
  - "kotlin-js-store/**"
---

# Build scripts and the version catalog

**`libs.versions.toml` carries the reasoning for every non-obvious pin inline. Update those comments
when bumping; never delete them.** For a Compose, BOM, Kotlin or material3 bump, follow the
`dependency-bump` skill — it has the verification procedure.

## Fails silently

1. **Never drop `composeMultiplatform` below 1.12.** Below it, every emoji and non-Latin script
   renders as tofu in the browser, and everything still builds. See the `compose-fonts` skill.
2. **material3 is on its own line — `composeMaterial3`, not `composeMultiplatform`**, and so is
   `composeMaterial3Adaptive`. CMP's `compose.material3` accessor would drag AndroidX material3
   backwards.
3. **material3 is deliberately outside the BOM.** A direct version beats the BOM's lower 1.4.0.
   Re-check after every BOM bump: if the BOM ever pins it *higher*, the BOM silently wins.
4. **The AndroidX Compose BOM is for Android configurations only** (`:app`, and the
   `androidMain.dependencies` of `:ui` and `:presenter`). **Never in `commonMain`** — it would drag
   jvm/native/web onto the AndroidX line.
5. **`circuit` and `metro` bump together.** Metro 1.4.2 is the floor for `@CircuitSerializable`
   registrations. An older one compiles and contributes nothing. Run `:shared-compose:allTests`.
6. **A `dataresult`/`uistate` bump can break the iOS build**, since both are exported to Swift in
   full. Run `:apple:macosArm64Test`, then the iOS build.

## Build requirements for Compose modules

- **Apply `alias(libs.plugins.compose.multiplatform)`**, even though every dependency is a catalog
  coordinate, because it configures skiko's web packaging. Keep `plugin.compose` alongside it.
- **`org.jetbrains.compose.experimental.macos.enabled=true`** stays in `gradle.properties`.
- **`android { androidResources { enable = true } }`** inside `kotlin { }` wherever there are
  `composeResources`. Without it the app hits `MissingResourceException` at runtime.
- **`binaries.executable()` on `js` and `wasmJs`** for any Compose *library* with browser test
  tasks. CMP 1.12's `checkComposeUiTestConfigurationFor{Js,WasmJs}` hard-fails otherwise, with no
  opt-out.
- A `platform(...)` in a KMP source-set `dependencies { }` block must be
  `project.dependencies.platform(...)`.

## General

- **Library modules apply `id("kmp-library")`, never `com.android.library` or
  `org.jetbrains.kotlin.plugin.parcelize`.** Use `alias(libs.plugins.kmp.parcelize)`. `:web`,
  `:desktop` and `:apple` apply no `kmp-library`.
- **Android modules must not apply `org.jetbrains.kotlin.android`** (AGP 9 has built-in Kotlin).
- Kotlin-family plugins (`plugin.compose`, `plugin.parcelize`, `plugin.serialization`) and
  `org.jetbrains.compose.hot-reload` are applied **by id with no version**. The root buildscript
  classpath forces the KGP and Compose-compiler versions Metro needs, so check that force when
  bumping `kotlin`. **`kotlin` is pinned to 2.4.20 for Swift export.**
- Modules target **Java 17** via `Versions` in `build-logic`. A module script can
  `import io.github.solcott.countries.build.Versions` — `Versions.class` rides in the same
  `build-logic.jar` as the plugin descriptors, so applying any convention (every module applies at
  least `formatting`) puts it on the script's classpath. A one-off module imports it rather than
  earning a new convention, which is why `kmp-library` and `app` are the only two.
  `build-logic`'s own `jvmToolchain(25)` is a different fact: the JVM the convention plugins
  compile against, matching the daemon.
- `settings.gradle.kts` applies the foojay resolver so Gradle can auto-provision missing JDKs.
- `settings.gradle.kts` stays on `RepositoriesMode.PREFER_SETTINGS`. The Kotlin plugin registers
  project repos for Node/Yarn/Binaryen, which `FAIL_ON_PROJECT_REPOS` rejects.
- **Two npm lockfiles**, `kotlin-js-store/yarn.lock` (js) and `kotlin-js-store/wasm/yarn.lock`
  (wasmJs). Regenerate both with `kotlinUpgradeYarnLock` **and** `kotlinWasmUpgradeYarnLock`. Never
  hand-edit them. "Lock file was changed" names only one of the two tasks.
- **`ktfmtCheck` at the root does not cover `build-logic`** (an included build). Run
  `./gradlew -p build-logic ktfmtCheck` too.
- detekt's live config is `config/detekt/detekt.yml`, applied by the `detekt` convention.
