# Decision Log

## Why this architecture?

I used a **multi-module MVI architecture with Circuit and Metro**, split into
`model → network → repository → presenter → ui → app`.

- **Modules** enforce the boundaries by making violations *not compile*: `presenter`/`ui`
  physically cannot reach Apollo types, and only `app` can see everything. This is stronger
  than package conventions and keeps build times parallelizable.
- **Circuit** gives a small, explicit MVI contract: a `Presenter` produces immutable
  `State`; the `Ui` is a pure function of that state and emits `Event`s. That makes the
  state holder trivial to test without Android or Compose UI.
- **Metro** wires the graph and (via its Circuit codegen) generates the presenter/UI
  factories from `@CircuitInject`, so there is no manual factory boilerplate.

## Why Circuit and Metro together
- UI and Presenters are completely decoupled.  UI Composables don't need to know anything about
  the presenter.  They only need to know about the state it produces and the events it handles.
- This is made even better with the autowiring made possible by Metro.
- Easy to extract composite presenters or use state producers to simply code and promote code reuse
- Compose runtime moves into the presenter

## How is UI state managed?

State is **unidirectional** and owned by the presenter (the equivalent of a ViewModel):

- The presenter returns a single immutable `CountryListScreen.State` holding the search
  text, the countries load-state, the continents load-state, and the selected continents.
- The UI renders that state and sends `Event`s (`CountryClicked`, `ToggleContinentSelection`,
  `Retry`) back through an `eventSink`.
- Repository flows are bridged into Compose state with `produceRetainedState`, and the
  reload counter uses `retain`, so in-flight data survives configuration changes.
- Loading/error/success is modeled explicitly with a `Response<T>` sealed type
  (`Loading` / `Data` / `Error`) in `model`, mapped from `ApolloResponse` in the repository.

## Why is the networking layer structured this way?

- `network` owns the Apollo client (provided through Metro in `NetworkProviders`), the
  `.graphql` operations, the generated code, and thin `CountriesApi` / `ContinentsApi`
  interfaces backed by Apollo implementations.
- `repository` turns Apollo's `ApolloResponse` flows into domain `Response<T>` and maps
  generated types to `model` types (`Mappers.kt`).
- `presenter` and `ui` depend only on `model`, so **no raw network model reaches the UI**.

## How did I implement filtering, and why?

Filtering is **server-side**, driven by the Countries API's `CountryFilterInput`:

- **Name** → a case-insensitive "starts with" regex, built in the `network` layer
  (`asStartsWithOperator`) as `^<chars>…`. The API compiles regexes without flags, so
  case-insensitivity is handled at the query-building layer.
- **Continent** → an `in` set-membership match on continent codes for multi-select.
- A null/empty value is sent as an **absent** sub-field so it imposes no constraint — an
  absent/empty filter returns all countries. (An empty operator object `{}` would match
  *none* on this API, so the filter is assembled in Kotlin rather than inlined.)

**Where the filter state lives:** in the `CountryListPresenter`. The search text is a
`TextFieldState`; the selected continents are a `rememberSaveable` snapshot list. The query
is derived reactively: `combine(snapshotFlow(text).debounce(300ms), snapshotFlow(selected))`
→ `transformLatest { repository.countriesAsFlow(name, codes) }`. Typing or toggling a
continent re-issues the query; `transformLatest` cancels the stale request; the 300 ms
debounce keeps keystrokes from spamming the network.

**Client-side vs server-side tradeoff:**

- *Server-side (chosen):* no need to load all 250 rows up front; scales to large datasets;
  demonstrates real GraphQL variables; each filter result is cached by Apollo under its
  argument key. Costs: a network round-trip per distinct filter (mitigated by debounce +
  cache), each filter combination is a separate cache entry, and the continent filter
  works on codes rather than display names.
- *Client-side (rejected):* fetch once, filter in memory — instant, offline-friendly,
  simpler — but doesn't scale, and wouldn't show any GraphQL query capability, which is a
  core thing this exercise is evaluating.

## How is the Kotlin Multiplatform migration structured?

The app was originally Android-only. It is now migrating to KMP **bottom-up, one module at a
time** — `model` first, then `network`, `repository`, `presenter`, `ui`. `model` is the leaf of
the dependency graph with no dependencies of its own, so it exercises the whole build setup
without any library-compatibility risk. Targets: android, jvm, iosArm64, iosSimulatorArm64,
macosArm64, js, wasmJs.

Everything platform-related lives in a single `kmp-library` convention plugin in `build-logic`,
alongside the existing `library` (Android-only) and `app` plugins. Module build files stay two
lines. `library.gradle.kts` is retired when the last module migrates.

**Why `com.android.kotlin.multiplatform.library` rather than `com.android.library`.** Not a
preference — a requirement. As of AGP 9, the Kotlin Multiplatform plugin is no longer compatible
with `com.android.application`/`com.android.library` in the same module; the legacy path is
opt-in under AGP 9 and removed in AGP 10. The new plugin also moves Android config into an
`android { }` block *inside* `kotlin { }` and is single-variant (no debug/release, no
`BuildConfig`, no view binding, resources off by default). None of that costs `model` anything,
and Android consumers still resolve the right variant through Gradle module metadata — `:app`,
`:ui`, `:presenter`, `:repository` and `:network` needed no changes at all.

**Why `kmp-parcelize` for `Continent`.** `Continent` is `Parcelable` because
`CountryListPresenter` keeps the selected continents in `rememberSaveable`, which needs them to
survive process death. `kotlin-parcelize` requires an AGP Android plugin and does not support
the KMP Android library plugin, so it can't come along. Three options:

- *`expect`/`actual` typealiases* (`CommonParcelize` → `kotlinx.parcelize.Parcelize` on Android,
  no-op elsewhere) — the usual community workaround, but it still leans on `kotlin-parcelize`
  underneath, which is the part that doesn't work here.
- *Drop `Parcelable` and use a `listSaver` in the presenter* — works, but pushes a persistence
  concern into the presenter and has to be repeated for every future saved type.
- *[`kmp-parcelize`](https://github.com/solcott/kmp-parcelize)* (chosen) — a Gradle plugin that
  provides `@Parcelize`/`Parcelable` for common code, lowering to a real `android.os.Parcelable`
  with a generated `CREATOR` on Android and to no-ops everywhere else. `Continent.kt` changed by
  exactly two imports, and nothing downstream changed.

**Why `PREFER_SETTINGS` for repositories.** The js/wasmJs targets download their own toolchains
(Node, Yarn, Binaryen), and the Kotlin plugin registers project-level repositories to do it —
which `FAIL_ON_PROJECT_REPOS` rejects at *registration* time, so pre-declaring those repositories
in settings isn't enough on its own. `PREFER_SETTINGS` ignores project repositories instead of
failing, preserving the same "everything resolves from settings" guarantee. The three toolchain
repositories are declared in `settings.gradle.kts` with `content { includeModule(...) }` filters
so each can only ever serve the one artifact it exists for.

**`network`: how much of Apollo actually survives the jump.** More than expected. All four Apollo
artifacts in use — `apollo-runtime`, `apollo-api`, `normalized-cache` and `normalized-cache-sqlite`
— publish every one of the seven targets, `wasm-js` included. The Apollo Gradle plugin detects the
KMP plugin itself, reads operations from `src/commonMain/graphql` and attaches generated code to
`commonMain`, and adds `-lsqlite3` to native binaries once it sees the SQLite cache dependency. So
`ContinentsApi.kt` and `CountriesApi.kt` moved without a single edit, and the build file needed no
Apollo source wiring. The migration was a directory move plus a dependency-block reshape.

**Why `platformConfiguration()` rather than a nullable cache factory.** `SqlNormalizedCacheFactory`
is itself an `expect` function inside Apollo, so `SqlNormalizedCacheFactory("countries.db")` in fact
compiles on every target — the cache did not *have* to become a platform seam at all. It became one
anyway, as an `expect fun ApolloClient.Builder.platformConfiguration()`, for two reasons. The web
actual is genuinely different: it passes no name, because SQLDelight's worker owns storage and the
name is discarded, and that is worth stating in code rather than hiding behind an argument that
silently does nothing. And it gives per-platform HTTP engines, interceptors and cache sizing a
declared home before they are needed. What does *not* vary — the endpoint, the in-memory tier, the
Metro provider — stays in `commonMain`, with a shared `memoryCacheBackedBy` helper so the four
actuals are one line each.

**Why real SQL.js persistence on web rather than memory-only.** Memory-only would have been two
lines and no npm dependencies, but it would have made web the one platform that silently forgets
everything on reload — a difference that shows up as a bug report, not as a build failure. The cost
is two npm packages on `jsMain`/`wasmJsMain`, pinned to SQLDelight 2.1.0 because that is what
`normalized-cache-sqlite` 1.0.6 actually depends on. A browser application module will additionally
need a webpack step to copy the `.wasm`; a library module does not, so that is deferred rather than
guessed at.

**`repository`: one import, and the first real test suite.** The entire module was one line away
from `commonMain` — `android.util.Log` in `Mappers.kt`. Everything else it touches (Apollo's
response and exception types, `isFromCache`, coroutines, Metro) was already multiplatform, so
unlike `network` this module ended up with **no `expect`/`actual` at all** and no platform source
sets.

Logging moved to [Kermit](https://kermit.touchlab.co/). An `expect`/`actual` logger would have
avoided the dependency, but it is five files hand-rolling a logging library for a single call
site, and the next module needing logging would either duplicate it or take a dependency on
`repository`. Dropping the log line was tempting — the exception already reaches the presenter
inside `DataError.Unknown(cause, message)` — but silently deleting diagnostics during a migration
is exactly the kind of change nobody notices going wrong.

**The logger is injected, not global.** `mapToOutcome` takes a `Logger` parameter, the repository
implementations take one through their Metro constructors and re-tag it per class, and the root
`Logger` is provided once by the graph. The obvious shortcut — a file-level
`private val logger = Logger.withTag(…)` — is what this replaced, and it fails on both counts that
matter. It makes logging untestable, whereas an injected logger can be driven by a `TestLogWriter`:
`MappersTest` now pins that a failure is logged *with its throwable* and, more usefully, that cache
misses and GraphQL errors are deliberately **not** logged — behaviour that is easy to break
silently. And it bypasses global configuration: Kermit's extensions (`kermit-crashlytics`,
`kermit-ktor`) attach writers to a configured root logger, so anything reaching for a static
`Logger` would quietly miss crash reporting.

The migration was also the moment to close the "mapping is untested" gap listed below.
`mapToOutcome` and `toDataError` are pure functions over Apollo types, and everything needed to
test them turns out to be public API: `ApolloResponse.Builder` is constructible directly, and
`isFromCache` reads a `CacheInfo` execution-context element that a test can attach itself — so
`Origin` tagging is testable without a real normalized cache. The resulting 18 tests live in
`commonTest` and run on all eight platform test runners, which is the first time this project has
exercised its KMP test infrastructure at all: the previous two modules had `commonTest` wired but
empty, so every runner had been reporting `NO-SOURCE`.

## What tradeoffs did I make due to time constraints?

- Minimal error handling/presentation (generic messages, swallowed cache misses).
- Normalized caching (memory → SQLite) was added but not deeply tuned; first launch still
  hits the network, and cache hits are per-exact-filter.
- ~~Tests focus on the presenter; mapping and the query builder are untested.~~ Mapping is now
  covered (`repository/src/commonTest`, running on all eight platform runners). The query builder
  in `network` — `asStartsWithOperator` and friends — is still untested.

---

# Walkthrough Notes

**Which file best reflects my engineering approach?**
`presenter/CountryListPresenter.kt`. It shows the whole philosophy in one place: immutable
state out, events in, repository flows bridged into Compose, and reactive server-side
filtering composed from `snapshotFlow` + `debounce` + `transformLatest`. It's also fully
unit-testable without Android.

**Which part would I refactor first?**
Circuit Presenters can get large quickly.  I would refactor the loading/filtering of countries and
loading of continents into Composite Presenters and/or StateProducers.  I would also migrate the XML
layouts to compose as there is almost no reason to develop new features using XML anymore.

**Most fragile or incomplete part?**
Error handling and its interaction with the cache. Cache-miss exceptions are swallowed to
make cache-first reads fall through to the network, which is correct but blunt — a genuine
persistent-store failure would be silently dropped the same way. Error *display* is generic.
Displaying better error messaging, especially errors that are actionable or provide usable info to
the users such as this device is offline.

---

# Unit Testing Notes

**What I tested.** The list presenter's state and logic, using Circuit's `presenterTestOf`
with fake repositories (`CountryListPresenterTest`):

- countries load → `loading` then `Data`;
- a load failure → `error` state with the message surfaced;
- tapping a country → navigation to `CountryDetailScreen`;
- continents load → `loading` then `Data`;
- selecting/deselecting a continent → toggles `selectedContinents`.

**Why these areas.** The presenter is where this app's actual logic and data flow live —
loading/error/success handling, the filter trigger, and navigation. It's also the highest
value-per-test surface: deterministic, framework-light, and no Android dependency. This
directly targets the exercise's emphasis on state management and filtering data flow.

**What I'd add with more time.**

- The name-prefix regex builder (`asStartsWithOperator`) and the continent-code mapping —
  pure functions, cheap and high-value, and the exercise specifically calls out testing
  filtering logic.
- The detail presenter's `loading` / `countryNotFound` / `error` branches.
- A debounce/`transformLatest` test asserting stale filter requests are cancelled.

> Note: these tests use JUnit + Circuit's test harness (`presenterTestOf` / `awaitItem`).
> Turbine is in the version catalog but not used by the current tests.

---

# AI Usage Note

> Draft — please adjust this to match your own recollection before submitting; it is your
> honesty statement.

AI (Claude) was used as a pair-programming assistant. It helped with: the initial
multi-module scaffolding (Gradle wiring for Apollo, Circuit, and Metro under AGP 9), drafting
the server-side filter query and the name-prefix operator, diagnosing an Apollo normalized-
cache `CacheMissException`, and drafting this documentation from the actual code.

Before using AI to generate the project I defined the desired app architecture
(module structure and boundaries, Unidirectional data flow, MVI/Circuit, DI/Metro, Apollo Kotlin, Compose for list view, 
XML for detail view via AndroidView, AGP version).  I meant to instruct Claude to only scaffold the
project, but forgot.  After the project was created I looked at the generated code and found things
I generally liked(XML layout, Metro setup, basic compose design for list view).  I also found some things
I didn't care for(repository returned data directly using suspend functions, actual back button in detail XML view, repeating Gradle configs in all or most modules).  
It also missed things. No real Material theme, no app bar.

I made the substantive decisions and edits myself: choosing the stack and module
boundaries, restructuring the build into `build-logic` convention plugins, designing and
implementing the `Continent` feature and multi-select filtering, the `Response<T>` type and
the error-mapping in `mapToResponse`, and the unit tests.

Here is a somewhat list of items I worked on by myself after the intial project creation:

- Migrated repository/api functions to return flow of items,with loading and errors included
- Search by name and Filter by continents from UI -> repository -> graphql
- Added Material theme(compose and XML) and app bar
- Consolidate build logic into build-logic convention plugins

I verified correctness by building and running on an emulator — the list loads live data,
the XML detail screen renders through `AndroidView`, navigation and the back stack work —
and by running the unit test suite.



