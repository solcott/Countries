# Countries

A small Kotlin Multiplatform app that lists world countries from the public
[Countries GraphQL API](https://countries.trevorblades.com/), lets you filter them by
name and continent, and drills into a detail screen for each one.

It started as an Android app and every library module is now multiplatform. There are four
entry points today — the Android app (`:app`), a Compose Multiplatform browser app (`:web`,
targeting both Kotlin/JS and Kotlin/Wasm), a Compose Multiplatform desktop app
(`:desktop`, for Windows, Linux and macOS), and a **native SwiftUI app** for iOS, iPadOS and
macOS (`iosApp`, on top of the `:apple` bridge module).

The first three share their UI as well as their presentation and data code. The SwiftUI app
shares everything *except* the UI: its screens are hand-written SwiftUI, deliberately built from
Apple idioms rather than as a port of the Compose design, while the Circuit presenters, the
repositories and the Apollo cache are the same Kotlin the other three run. Presenters reach Swift
through [Molecule](https://github.com/cashapp/molecule) (a `@Composable` presenter becomes a
`StateFlow`) and [SKIE](https://skie.touchlab.co/) (sealed types become exhaustive Swift enums,
flows become `AsyncSequence`s).

## API choice

I used the **Countries GraphQL API**. It is a good fit for this exercise because it
supports **server-side filtering** (regex on name, set-membership on continent), so the
filter feature can be demonstrated as real GraphQL query variables rather than in-memory
list filtering. It is also stable, unauthenticated, and small enough to reason about.

## Requirements coverage

| Requirement | Where |
| --- | --- |
| Fetch a list from a GraphQL API | `network` (Apollo Kotlin) |
| Display the list in Jetpack Compose | `ui/CountryListUi.kt` — Compose Multiplatform, shared by Android and the browser |
| Filter / search the list | name search + continent multi-select (server-side) |
| Detail screen built with **XML layouts** | Originally `res/layout/view_country_detail.xml` via `AndroidView`; since converted to Compose in `ui/CountryDetailUi.kt` ahead of the Kotlin Multiplatform migration |
| Loading / error / success states | `model/Response.kt`, surfaced through the presenters |
| Mapping layer (no raw network models in the UI) | `repository/Mappers.kt` (generated → `model`) |
| Kotlin throughout | ✅ |
| Unit tests | `presenter/…/CountryListPresenterTest.kt` |

## How to run

A **JDK is auto-provisioned** by the Gradle daemon via the foojay resolver (Amazon
Corretto 25), so you do not need to install one manually.

### Android

Requirements: the **Android SDK** with API 37 installed (`compileSdk = 37` — see the note
below), and an emulator or device on **API 28+** (`minSdk = 28`).

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # install on a running emulator/device
./gradlew test                 # run unit tests
./gradlew ktfmtFormat          # apply formatting
```

Then launch the **Countries** app from the launcher, or:

```bash
adb shell am start -n io.github.solcott.countries/.MainActivity
```

### Browser

No Android SDK needed. Gradle downloads Node and Yarn itself.

```bash
./gradlew :web:wasmJsBrowserDevelopmentRun   # Kotlin/Wasm, http://localhost:8080
./gradlew :web:jsBrowserDevelopmentRun       # Kotlin/JS, same app
```

Both produce a static bundle you can serve from anywhere:

```bash
./gradlew :web:wasmJsBrowserDistribution     # → web/build/dist/wasmJs/productionExecutable
```

Routes are hashes, so the bundle needs no server-side rewriting and links are shareable:
`#/` is the list, `#/country/FR` opens France. Browser back and forward work.

### Desktop

No Android SDK needed.

```bash
./gradlew :desktop:run
```

`Esc`, `Cmd+[` and `Alt+←` navigate back from the detail screen. To build something
installable:

```bash
./gradlew :desktop:packageUberJarForCurrentOS      # → desktop/build/compose/jars
./gradlew :desktop:packageDistributionForCurrentOS # .dmg / .msi / .deb, host OS only
```

Both are **host-OS builds** — the Skia runtime is selected by OS and architecture, and
jpackage cannot cross-build, so a Windows installer has to be produced on Windows.

### iOS, iPadOS and macOS

Needs Xcode. Open the project and run — the Kotlin framework is built by a Run Script phase, so
there is no Gradle step to remember:

```bash
open iosApp/Countries.xcodeproj
```

The same target covers all three: `NavigationSplitView` collapses to push-and-pop on iPhone and
becomes two columns on iPad and Mac. Minimum versions are iOS 17 and macOS 15.

The Swift tests — unit tests plus XCUITest coverage of selection, search, filtering and the iPad
two-column layout — and the Kotlin bridge's own tests:

```bash
xcodebuild test -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
xcodebuild test -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPad mini (A17 Pro),OS=18.4'
./gradlew :apple:macosArm64Test :apple:iosSimulatorArm64Test
```

Run both simulator destinations: several UI tests are device-shape specific and skip themselves on
the other. Tests run on simulators only — the macOS destination is for building and running the
app. The UI tests drive the live API, so a cold simulator needs network.

Apple Silicon only — the Kotlin targets are `iosArm64`, `iosSimulatorArm64` and `macosArm64`.
There is no Mac Catalyst build, and there cannot be: Kotlin/Native has no Catalyst target.

### Cleaner `git blame`

Bulk formatting commits are listed in [`.git-blame-ignore-revs`](.git-blame-ignore-revs) so
they don't obscure real authorship. GitHub honors this file automatically; for local
`git blame` (and IDE blame), opt in once per clone:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

## Architecture at a glance

Eleven Gradle modules, dependencies flowing strictly downward:

```
app            → Android entry point: Activity, theme, manifest
web            → Browser entry point (js + wasmJs): main(), index.html, URL routing
desktop        → Desktop entry point (jvm): main(), Window, keyboard back, flag font
apple          → Apple bridge (ios + macos): CountriesKit.xcframework for iosApp/ (SwiftUI)
shared-compose → ComposeGraph — the Metro graph every Compose app shares
shared         → CoreGraph for non-Compose consumers, plus the root Logger
ui             → Compose UI (Circuit Ui), and CountriesApp — the app every entry point mounts
presenter      → Circuit Screens, presenters, state, events  (the state holders)
repository     → domain-facing data access, generated → model mapping
network        → Apollo client, .graphql operations, generated code
model          → plain Kotlin domain types + Response<T>
```

Everything from `shared-compose` down is Kotlin Multiplatform and builds for Android, JVM,
iOS, macOS, js and wasmJs. `app`, `web` and `desktop` are the only platform-specific modules,
and each does the same two things: build the Metro graph, and hand its `Circuit` to
`CountriesApp`.

- **UI:** Jetpack Compose throughout.
- **Architecture:** MVI via [Circuit](https://slackhq.github.io/circuit/) — presenters own
  state, UI is a pure function of state and emits events.
- **DI:** [Metro](https://zacsweers.github.io/metro/), including Circuit factory codegen.
- **GraphQL:** Apollo Kotlin, with a normalized cache (in-memory → SQLite).

See [DECISION_LOG.md](DECISION_LOG.md) for the reasoning behind these choices, the filtering
design, the testing strategy, and an AI-usage note.

## Assumptions

- The continent filter matches on the API's continent **code** (`EU`, `AS`, …), not the
  display name; the UI selects real `Continent` values so the code is always available.
- Network is assumed available for the first load; the SQLite cache then serves repeat
  launches for previously fetched queries.

## Limitations / known issues

- **Error handling is intentionally minimal.** Failures are mapped to a generic
  `Response.Error` with the first available message and a Retry affordance. Cache-miss
  exceptions are swallowed so a cache-first read falls through to the network. Presenting
  errors well (distinguishing offline vs. server vs. parse, inline vs. full-screen) is the
  first thing I would invest more time in.
- **Caching is per-query-arguments.** Each distinct filter is its own normalized-cache
  entry, so offline/repeat benefits apply only to *identical* filters, not arbitrary ones.
- **Test coverage is deliberately narrow** (see the testing notes) — presenter/state logic
  only; mapping and the filter query builder are not yet covered.

## What I would improve with more time

1. Richer error handling and presentation (typed errors, offline detection, snackbars vs.
   full-screen states).
2. Unit tests for the name-prefix regex builder, the continent-code mapping, and the
   generated → `model` mappers.
3. Empty-state UI (e.g. a filter that matches nothing) and a clear "no results" message.
4. Instrumented tests for the Compose list and detail screens.
