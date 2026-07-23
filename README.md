# Countries

A small Android app that lists world countries from the public
[Countries GraphQL API](https://countries.trevorblades.com/), lets you filter them by
name and continent, and drills into a detail screen for each one.

## API choice

I used the **Countries GraphQL API**. It is a good fit for this exercise because it
supports **server-side filtering** (regex on name, set-membership on continent), so the
filter feature can be demonstrated as real GraphQL query variables rather than in-memory
list filtering. It is also stable, unauthenticated, and small enough to reason about.

## Requirements coverage

| Requirement | Where |
| --- | --- |
| Fetch a list from a GraphQL API | `network` (Apollo Kotlin) |
| Display the list in Jetpack Compose | `ui/CountryListUi.kt` |
| Filter / search the list | name search + continent multi-select (server-side) |
| Detail screen built with **XML layouts** | `ui/CountryDetailUi.kt` + `res/layout/view_country_detail.xml` via `AndroidView` |
| Loading / error / success states | `model/Response.kt`, surfaced through the presenters |
| Mapping layer (no raw network models in the UI) | `repository/Mappers.kt` (generated → `model`) |
| Kotlin throughout | ✅ |
| Unit tests | `presenter/…/CountryListPresenterTest.kt` |

## How to run

Requirements:

- **Android SDK** with API 37 installed (`compileSdk = 37` — see the note below).
- A **JDK is auto-provisioned** by the Gradle daemon via the foojay resolver (Amazon
  Corretto 25), so you do not need to install one manually.
- An emulator or device on **API 28+** (`minSdk = 28`).

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

## Architecture at a glance

Six Gradle modules, dependencies flowing strictly downward:

```
app          → wires the Metro dependency graph, hosts the Activity
ui           → Compose UI + the XML detail screen (Circuit Ui)
presenter    → Circuit Screens, presenters, state, events  (the state holders)
repository   → domain-facing data access, generated → model mapping
network      → Apollo client, .graphql operations, generated code
model        → plain Kotlin domain types + Response<T>
```

- **UI:** Jetpack Compose (list) + an XML layout via `AndroidView` (detail).
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
- **ktfmt does not currently format the Android modules' Kotlin sources.** ktfmt-gradle
  0.26 doesn't detect AGP 9's built-in Kotlin, so per-source-set format/check tasks are
  only created for the pure-Kotlin `model` module. Fix is pending upstream
  ([ktfmt-gradle#…](https://github.com/cortinico/ktfmt-gradle)); formatting is effectively
  unenforced on the Android modules until then.
- **Test coverage is deliberately narrow** (see the testing notes) — presenter/state logic
  only; mapping and the filter query builder are not yet covered.

## What I would improve with more time

1. Richer error handling and presentation (typed errors, offline detection, snackbars vs.
   full-screen states).
2. Unit tests for the name-prefix regex builder, the continent-code mapping, and the
   generated → `model` mappers.
3. Empty-state UI (e.g. a filter that matches nothing) and a clear "no results" message.
4. Instrumented tests for the Compose list and the XML detail interop.
