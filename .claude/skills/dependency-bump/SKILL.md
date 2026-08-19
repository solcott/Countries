---
name: dependency-bump
description: How Compose, Kotlin and the version catalog are pinned in this project, and how to change one without silently breaking another. Use when editing gradle/libs.versions.toml, bumping the Compose BOM or Compose Multiplatform, changing material3, or adding an npm dependency to a web target. Several of the constraints here fail with no build error at all — a wrong Compose version renders tofu in the browser and a wrong BOM silently drags material3 backwards.
---

# Bumping dependencies

Versions live in `gradle/libs.versions.toml`. See `AGENTS.md` for the module map and the
project-wide conventions this sits inside.

## Read the catalog's own comments first

`libs.versions.toml` carries the reasoning for every non-obvious pin inline, next to the pin. It is
the authoritative record — this skill covers what the catalog cannot: the wiring, the verification,
and the failure modes.

**Do not delete those comments when bumping a number.** Update them. They exist because every one
of them documents a decision that a later bump would otherwise silently undo.

## Where Compose comes from, and why it is split

Compose in a migrated module comes from **two** places, and the split is not arbitrary:

| Need | Artifact | Why |
| --- | --- | --- |
| `runtime`, `runtime-saveable` | `org.jetbrains.compose.*` | Thin aliases; `androidx.compose.runtime` is already multiplatform |
| `foundation` (incl. `TextFieldState`), `ui`, `material3` | `org.jetbrains.compose.*` | **The AndroidX equivalents are Android-only** — they publish `android` plus `jvmstubs`/`linuxx64stubs`, which are not real implementations |
| `retain` | `androidx.compose.runtime:runtime-retain` | Multiplatform already, and has **no** Compose Multiplatform equivalent |
| strings, drawables | `org.jetbrains.compose.components:components-resources` | The multiplatform replacement for Android `res/` |

**The AndroidX Compose BOM aligns the Android side; Compose Multiplatform owns everything else.**
That division is the whole versioning story, and it is worth stating because the failure it
prevents is silent. Without the BOM only `ui` and `runtime` were declared anywhere, so only they
followed the `composeUi` pin; nothing declared `foundation` or `animation`, so those drifted to
whatever CMP and material3 happened to request — 1.12.0-beta01 while the rest of `:app` was on
1.12.0. Nothing warns about that.

`androidx.compose:compose-bom` is applied to **Android configurations only** — `:app`, and an
`androidMain.dependencies` block in `:ui` and `:presenter`, the only two KMP modules that pull
Compose. **It must never go in `commonMain`:** `androidx.compose.runtime` is genuinely
multiplatform and reaches jvm/native/web through CMP's thin alias, so a common-scoped BOM would
drag those onto the AndroidX line too.

## The four rules that fail silently

**1. Never drop `composeMultiplatform` below 1.12.** 1.12 is where Compose Multiplatform gained
automatic fallback-font loading in the browser. Dropping below it reintroduces the bug with no
error: everything builds, and only a human looking at the running page notices that every emoji and
every non-Latin script renders as tofu. Read the `compose-fonts` skill before even considering it.

**2. material3 is on its own version line — `composeMaterial3`, not `composeMultiplatform`.** It
does not track the core version and never has; the CMP plugin's own `compose.material3` accessor
pins something far behind, which would drag AndroidX material3 *backwards* several minor lines.
Always set `composeMaterial3` explicitly, and when changing it check two things: which CMP core
version it requires, and which AndroidX material3 it aliases. The same reasoning applies to
`composeMaterial3Adaptive`, which is also on its own line.

**3. material3 is deliberately outside the BOM.** The BOM manages it at 1.4.0, older than the alpha
line this project tracks. That is harmless *because* a direct dependency with an explicit version
beats a lower BOM constraint: `:app` resolves 1.5.0-alpha26 from the catalog and `:ui` resolves
1.5.0-alpha22 through CMP's material3. Both resolve **up** from 1.4.0, never back.

**Re-check that after every BOM bump** — if a future BOM pins material3 *higher* than the alpha
line, the BOM silently wins and the project moves backwards from the alpha it meant to track.

Those two numbers differing is expected, not skew: material3 is on its own line by design, and
`:ui` gets it via Compose Multiplatform while `:app` declares AndroidX directly.

**4. `platform(...)` does not exist on a KMP source-set dependency handler.** It is not Gradle's
`DependencyHandler`, so an `androidMain.dependencies { }` block needs
`project.dependencies.platform(...)`. A bare `platform(...)` fails with `Unresolved reference` —
this one at least fails loudly, but the fix is not obvious.

## Verify — both configurations, every time

One module is not representative, so check `:app` (declares AndroidX directly) and `:ui` (gets
Compose through CMP):

```
./gradlew :app:dependencies --configuration debugCompileClasspath
./gradlew :ui:dependencies  --configuration androidCompileClasspath
```

Every `androidx.compose.{ui,foundation,animation,runtime}` artifact should read the same version on
both — currently 1.12.0. The two material3 numbers will differ; that is the expected outcome
described above, not a finding.

**Delegate this to the `gradle-runner` subagent's dependency-verification mode.** The raw output is
thousands of lines and the answer is six numbers.

## Three build requirements that are easy to miss

- **A Compose module must apply `alias(libs.plugins.compose.multiplatform)`**, even though every
  dependency is declared by catalog coordinate rather than through `compose.*` accessors.
  `compose.foundation` pulls `compose.ui`, which on js and wasmJs depends on
  `org.jetbrains.skiko:skiko`; that plugin is what configures skiko's web packaging. Keep
  `org.jetbrains.kotlin.plugin.compose` applied alongside it — on Kotlin 2.x the CMP plugin expects
  the Compose compiler plugin to be applied separately.
- **`org.jetbrains.compose.experimental.macos.enabled=true` in `gradle.properties`.** `macosArm64`
  is in the target list and the CMP plugin refuses to configure it without this opt-in, failing at
  configuration time with "Compose targets '[macos]' are experimental".
- **A module with `composeResources` needs `android { androidResources { enable = true } }`**
  inside its `kotlin { }` block — see `ui/build.gradle.kts`. The KMP Android plugin disables
  resource processing by default, which leaves `variant.sources.assets` unavailable, and that is
  exactly where Compose Multiplatform packages resources on Android. Without it everything
  compiles, the APK simply has no `assets/composeResources/`, and the app dies on first use with
  `MissingResourceException`. Nothing warns at build time.

Also: **add `binaries.executable()` to the `js` and `wasmJs` targets of any Compose *library*
module** — see `presenter/build.gradle.kts`. CMP 1.12 added
`checkComposeUiTestConfigurationFor{Js,WasmJs}`, which hard-fails any module whose browser test
bundle reaches skiko without an executable binary to bundle it into. It fires off the target's test
task existing, not off there being test sources, and there is no opt-out property.

## Kotlin, AGP and the plugin classpath

- **`kotlin` is pinned to 2.4.20 for Swift export**, not for anything on the JVM side. `StateFlow`
  arriving as `KotlinTypedStateFlow<T>` and sealed types getting a generated `sealedType()` both
  landed after 2.4.10. Dropping below 2.4.20 costs the two things that make the exported API usable
  from Swift at all — read the `apple-app` skill before touching it.
- **AGP 9 has built-in Kotlin support**, so Android modules must **not** apply
  `org.jetbrains.kotlin.android`; AGP fails the build if they do.
- The root buildscript classpath **forces** the KGP and Compose compiler plugin versions Metro
  needs. Modules apply the remaining Kotlin-family plugins (`plugin.compose`, `plugin.parcelize`)
  by id with no version, picking up those classpath versions. Bumping `kotlin` in the catalog
  without checking that force is how you get a Metro/KGP mismatch.
- All modules target **Java 17**, from `Versions` in `build-logic`. A module script can import it
  directly — `import io.github.solcott.countries.build.Versions` — because `Versions.class` rides
  in the same `build-logic.jar` as the plugin descriptors. `build-logic`'s own `jvmToolchain(25)`
  is a different fact: that is the JVM the convention plugins compile against, matching the daemon,
  not the modules' target.
- The daemon JVM is pinned in the root `build.gradle.kts`. After changing it, run
  `./gradlew updateDaemonJvm` to regenerate `gradle/gradle-daemon-jvm.properties`, which is
  committed.

## npm dependencies and the two web lockfiles

`kotlin-js-store/` holds **two** committed lockfiles, because js and wasmJs have separate npm
stores: `yarn.lock` for js and `wasm/yarn.lock` for wasmJs. Adding an npm dependency needs both.

```
./gradlew kotlinUpgradeYarnLock       # js
./gradlew kotlinWasmUpgradeYarnLock   # wasmJs
```

Regenerate rather than editing by hand. A build that touches only one store fails with "Lock file
was changed" naming only that task, so it is easy to fix one and forget the other — and the second
failure then looks like a new problem. Adding *tests* can also move a lockfile, because the JS test
link pulls in packages the main compilation did not.

`devNpm("copy-webpack-plugin")` is declared per target rather than once, because `npm()`/`devNpm()`
are only available to JS-family source sets.

## After any bump

1. `./gradlew ktfmtCheck test assembleDebug` for the JVM and Android side.
2. Both dependency verifications above, if Compose or the BOM moved.
3. `./gradlew :ui:assemble` — the cheapest way to prove every target still compiles against the
   new Compose.
4. If the browser is affected, actually look at the running page. The font failure has no build
   signal; see `compose-fonts`.
