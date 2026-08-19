---
name: verify
description: Works out the minimal set of builds and tests that actually prove a given diff, then hands execution to the gradle-runner and xcode-runner subagents. Use before committing, before pushing, and before opening or updating a PR. This project has six Kotlin targets and three non-Android app modules, so "run the tests" is ambiguous — several real failure modes only appear in a task nobody would think to run.
---

# Verifying a change

This is a **decision procedure, not a runner**. Work out the task set here, then delegate:
`gradle-runner` for Gradle, `xcode-runner` for `xcodebuild`. Both return a verdict instead of a
log, which is the point.

## Always

```
./gradlew ktfmtFormat
```

Formatting is enforced by tooling, not review, and CI runs `ktfmtCheck`. Run `ktfmtFormat` before
committing rather than discovering it in CI.

**`ktfmtCheck` at the root does not cover `build-logic`** — that is a separate included build. If
the diff touches a convention plugin, run it from inside `build-logic/` as well.

## Map the diff to a task set

Start from `git status --short` / `git diff --name-only`. Take the union of every row that matches.

| Changed | Run | Why this and not less |
| --- | --- | --- |
| `model/`, `dataresult/`, `uistate/` | `:<module>:assemble` and `:apple:macosArm64Test` | These three are exported to Swift **in full**. A Compose type or a generic sealed type added to any of them breaks the iOS build and nothing else warns you. |
| `network/` | **`:network:assemble`** and `:repository:allTests` | `:network` has no tests of its own. `assemble` is the task that catches the `compileWebMainKotlinMetadata` `Worker` failure — neither web target's own compile task reproduces it. See `network-apollo`. |
| `repository/` | `:repository:allTests` | |
| `presenter/` | `:presenter:allTests` | |
| `ui/` | `:ui:assemble` and `assembleDebug` | `:ui` has no tests; `assemble` proves it compiles on all six targets, `assembleDebug` proves the Android app still links. |
| `shared/`, `shared-compose/` | `assembleDebug` and `:desktop:packageUberJarForCurrentOS` | Graph changes fail at the module that declares `@DependencyGraph`, so build a consumer. |
| `web/` | `:web:wasmJsBrowserDistribution` and `:web:allTests` | `HistoryAction` and `Routes` are pure and tested; the distribution task is what proves the bundle still builds. |
| `desktop/` (code) | `:desktop:test` | |
| `desktop/` (packaging, `nativeDistributions`) | **`:desktop:packageDistributionForCurrentOS`** | `run` uses the full JDK, so a missing jlink module surfaces only in an *installed* build, as a crash on the first query. Never test packaging with `run`. |
| `apple/` | `:apple:macosArm64Test :apple:iosSimulatorArm64Test`, then the iOS build | |
| `iosApp/` | `xcodebuild` on **both** simulator destinations | Several UI tests are device-shape specific and skip themselves on the other shape. |
| `gradle/libs.versions.toml`, any `build.gradle.kts` | See `dependency-bump` | Compose or BOM changes need both dependency verifications. |
| `kotlin-js-store/`, an npm dependency | `kotlinUpgradeYarnLock` **and** `kotlinWasmUpgradeYarnLock` | Two separate stores. A build touching one fails naming only that task, so fixing one and forgetting the other is the usual cause of the next failure. |
| `.claude/skills/`, `AGENTS.md`, `README.md` | Nothing | Documentation. Confirm `git status` shows no source changes and stop. |

## The broad sweep

When the diff is wide, or before opening a PR:

```
./gradlew ktfmtCheck test assembleDebug
```

That is the JVM and Android side. It does **not** cover js, wasmJs, or the Apple targets — add
`:<module>:allTests` for whichever modules have tests (`apple`, `repository`, `presenter`, `web`)
and the relevant app-module build.

## What a green build does not prove

- **Only five places have tests** — `apple/src/commonTest`, `repository/src/commonTest`,
  `presenter/src/commonTest`, `web/src/commonTest`, `desktop/src/test`. Everything else has none,
  so a passing test task for `:ui` or `:network` ran nothing. Have `gradle-runner` report the
  count.
- **`UP-TO-DATE` is not a result.** Re-run with `--rerun-tasks` when verifying a change you just
  made.
- **Four of the six `allTests` runners have host requirements.** `jsBrowserTest` and
  `wasmJsBrowserTest` need Chrome; `macosArm64Test` and `iosSimulatorArm64Test` need Xcode and boot
  a simulator. A failure there may be the environment, not the code.
- **These fail at runtime with a clean build**, so no Gradle task catches them — look at the
  running app when the diff touches one:
  - A vector drawable with `?attr/…` or `@android:…` (CMP's parser cannot resolve either).
  - A missing `androidResources { enable = true }` → `MissingResourceException` on first use.
  - `composeMultiplatform` below 1.12 → every emoji and non-Latin script renders as tofu in the
    browser. See `compose-fonts`.
  - An empty `.appiconset` → an app with no icon. Verify in the built bundle, never from the log.
  - A missing jlink module in `:desktop`'s `nativeDistributions` → crash on first query, in an
    installed build only.

## Then look at it

For anything user-visible, run the app rather than trusting the build. The `run` skill covers
launching each of the five front ends; `android-cli` covers screenshots and UI inspection on
Android.

## Before pushing

1. `git status --short` — **never `git add -A`**; on some branches it sweeps up Xcode
   `xcuserdata/`. Stage by explicit path. See `pr-review`.
2. The task set above, green.
3. `./gradlew ktfmtCheck` clean, since that is what CI runs.
