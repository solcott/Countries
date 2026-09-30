---
name: dependency-bump
description: How to bump Compose, Kotlin or the Compose BOM in this project and prove nothing else moved — the AndroidX/CMP split, the two dependency verifications, and the after-bump checklist. Use when editing gradle/libs.versions.toml, bumping the Compose BOM or Compose Multiplatform, changing material3, or adding an npm dependency to a web target. Several of the constraints here fail with no build error at all — a wrong Compose version renders tofu in the browser and a wrong BOM silently drags material3 backwards.
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

## The invariants live in a rule

`.claude/rules/build-scripts.md` loads by itself whenever a build script, the catalog or a lockfile
is read. It holds the silent-failure list: the 1.12 floor, material3 on its own line, the BOM kept
out of `commonMain` and material3 kept out of the BOM, circuit+metro bumped together, the Compose
module build requirements, the Kotlin pin and the two lockfiles. This skill covers what a rule
cannot: why the split exists, and how to prove a bump did not move anything.

**material3 resolves up, never back.** The BOM manages it at 1.4.0, older than the alpha line this
project tracks, and a direct dependency with an explicit version beats a lower BOM constraint. `:app`
resolves the catalog's AndroidX version, and `:ui` resolves CMP's material3. The two numbers differ
by design. **After every BOM bump, re-check that the BOM has not pinned material3 above the alpha
line.** If it has, the BOM wins silently.

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

## After any bump

1. `./gradlew ktfmtCheck test assembleDebug` for the JVM and Android side.
2. Both dependency verifications above, if Compose or the BOM moved.
3. `./gradlew :ui:assemble` — the cheapest way to prove every target still compiles against the
   new Compose.
4. If the browser is affected, actually look at the running page. The font failure has no build
   signal; see `compose-fonts`.
