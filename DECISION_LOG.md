# Decision Log

## Why this architecture?

I used a **multi-module MVI architecture with Circuit and Metro**, split into
`model → network → repository → presenter → ui → shared → shared-compose → app`.

- **Modules** enforce the boundaries by making violations *not compile*: `presenter`/`ui`
  physically cannot reach Apollo types, and only the graph modules see broadly. This is stronger
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

**Why SQL.js on web rather than memory-only — and why that reasoning turned out to be wrong.**
The argument at the time was that memory-only would have been two lines and no npm dependencies,
but would have made web the one platform that silently forgets everything on reload. So the web
actual took SQLDelight's SQL.js worker driver, at the cost of two npm packages on
`jsMain`/`wasmJsMain`, pinned to SQLDelight 2.1.0 because that is what `normalized-cache-sqlite`
1.0.6 actually depends on. The webpack step to copy the `.wasm` was deferred to whenever a browser
application module appeared, rather than guessed at.

Building `:web` was the first time any of this ran, and it does not do what the paragraph above
assumed. The worker starts, loads `sql-wasm.wasm` and answers queries — all verified in a browser
— but `@cashapp/sqldelight-sqljs-worker` is thirteen lines whose database is
`new SQL.Database()`, held in memory and never written anywhere.
`createDefaultWebWorkerDriver()` has no persistence story at all. Web still forgets everything on
reload; it now does so via a 600 KiB wasm blob and a worker chunk instead of directly. Persisting
would mean a custom worker that serialises the database into IndexedDB — a real piece of work,
not a configuration flag.

The mistake was reasoning about a dependency's behaviour from its name and its docs' framing
rather than from what it does, and then not having anything that could catch it: no library test
exercises a browser, and the module compiled and shipped for two targets regardless. Left in
place rather than reverted, because deciding between "write the IndexedDB worker" and "drop to
memory-only on web and say so" is a product call, not a cleanup.

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
`Logger` is provided once by `LoggingProviders` in `shared`. The obvious shortcut — a file-level
`private val logger = Logger.withTag(…)` — is what this replaced, and it fails on both counts that
matter. It makes logging untestable, whereas an injected logger can be driven by a `TestLogWriter`:
`MappersTest` now pins that a failure is logged *with its throwable* and, more usefully, that cache
misses and GraphQL errors are deliberately **not** logged — behaviour that is easy to break
silently. And it bypasses global configuration: Kermit's extensions (`kermit-crashlytics`,
`kermit-ktor`) attach writers to a configured root logger, so anything reaching for a static
`Logger` would quietly miss crash reporting. `LoggingProviders` in `shared` is the single place
those writers get added.

The migration was also the moment to close the "mapping is untested" gap listed below.
`mapToOutcome` and `toDataError` are pure functions over Apollo types, and everything needed to
test them turns out to be public API: `ApolloResponse.Builder` is constructible directly, and
`isFromCache` reads a `CacheInfo` execution-context element that a test can attach itself — so
`Origin` tagging is testable without a real normalized cache. The resulting 14 tests live in
`commonTest` and run on every platform test runner, which is the first time this project has
exercised its KMP test infrastructure at all: the previous two modules had `commonTest` wired but
empty, so every runner had been reporting `NO-SOURCE`.

**`presenter`: where Compose stops being one library.** The interesting discovery is that
"Compose Multiplatform" and "AndroidX Compose" are no longer alternatives — they are pieces of one
graph, and the module needs both. `androidx.compose.runtime` is *already* multiplatform, and
`org.jetbrains.compose.runtime` turns out to be a thin alias onto it (its `runtime-desktop` jar is
literally empty apart from a licence file). But `androidx.compose.foundation` is **not**
multiplatform — it ships `android` plus `jvmstubs`/`linuxx64stubs` — so `TextFieldState`, which
`CountryListScreen.State` holds, has to come from Compose Multiplatform's foundation. Meanwhile
`retain` lives in `androidx.compose.runtime:runtime-retain`, which is multiplatform and has no CMP
equivalent. Three artifacts, three different reasons.

Dependencies are declared as plain catalog coordinates rather than through the `compose.*`
accessors, but the `org.jetbrains.compose` **plugin is still required** — because
`compose.foundation` pulls `compose.ui`, which on js and wasmJs depends on `skiko`, and that
plugin configures skiko's web packaging. It also gates `macosArm64` behind
`org.jetbrains.compose.experimental.macos.enabled=true`, without which configuration fails outright.

The Screens kept `@Parcelize` through **kmp-parcelize**, and this case is subtler than `Continent`
in `model`. Circuit's `Screen` is itself an `expect interface` — `: CircuitSaveable, Parcelable` on
Android, plain `: CircuitSaveable` everywhere else — so the Screens *inherit* their Parcelable-ness
from another library's `actual` rather than declaring it. That works: the Android artifacts contain
a real `Parcelable$Creator` and `writeToParcel`, verified by decompiling rather than inferred from
a clean compile.

Two things only showed up once the tests ran cross-platform. `SnapshotStateList.equals` is
structural on JVM/Android and identity-based on native and Kotlin/JS, so
`assertEquals(listOf(europe), state.selectedContinents)` had been passing by accident; it needed a
`.toList()`. And Molecule's js/wasm frame clock lives in its `browserMain` source set, so
`presenterTestOf` cannot advance recomposition under Node at all.

That second one settled an open question rather than needing a workaround: the web targets are for
a browser app, so `kmp-library` now declares `js { browser() }` and `wasmJs { browser() }` with no
`nodejs()`. Testing under Node was never going to work for a Compose project, and dropping it takes
the runner count from eight to six without reducing platform coverage — js and wasmJs are still
built and still tested, in a browser.

**`ui`: the last module, and the one where "it compiles" means least.** By this point the module
had no `android.*` imports left — converting the detail screen off `AndroidView` saw to that — so
the migration was entirely about resources. `androidx.compose.foundation`, `ui` and `material3` are
all Android-only, so those come from Compose Multiplatform; `res/` becomes
`commonMain/composeResources/` behind a generated `Res` class.

Two things bit, and both are the same shape: **they compile perfectly and fail at runtime.**

The four vector drawables tinted with `?attr/colorControlNormal` and filled with
`@android:color/white` — a theme attribute and a framework resource, neither of which CMP's parser
can resolve. Dropping the tint and inlining `#FFFFFFFF` is visually neutral, because every one of
these icons is drawn through `Icon`, which supplies `LocalContentColor` as a `ColorFilter` and
overrides whatever the vector carried.

The subtler one: the app built cleanly, installed, and died on launch with
`MissingResourceException`. The KMP Android plugin disables resource processing by default, which
leaves `variant.sources.assets` unavailable — and that is precisely where Compose Multiplatform
packages resources on Android. CMP takes the right code path (it explicitly supports
`com.android.kotlin.multiplatform.library`), registers nothing, and warns about none of it; the
APK simply ships without `assets/composeResources/`. One line —
`android { androidResources { enable = true } }` — fixes it.

Both are the argument for the verification habit this migration has used throughout: run it, don't
just build it. A green `assemble` said nothing useful here.

**material3 needed its own version.** Compose Multiplatform versions material3 separately from the
rest of the framework, and the two obvious choices are both wrong. The CMP plugin's own
`compose.material3` accessor pins 1.9.0, which aliases AndroidX material3 **1.4.0** — a minor line
backwards from the 1.5.0-alpha this project is on. Aligning material3 to `composeMultiplatform`
does not work either, since there is no 1.11.1. `1.11.0-alpha07` is the answer: the last one still
built against the 1.11 core line, and it tracks AndroidX material3 1.5.0-alpha13.

That pin held until the browser app shipped and forced the whole project onto 1.12 — see the fonts
section below. The *reasoning* survives the move even though the numbers did not: material3 is on
its own version line, and picking one means checking both which CMP core it requires and which
AndroidX material3 it aliases.

## Why is the detail screen no longer XML?

It was an XML layout hosted in `AndroidView` because the technical assessment asked for one, not
because view interop was a design goal — and the "refactor first" note above already flagged it as
something to migrate.

The KMP migration forced the question. `AndroidView` is Android-only and has no multiplatform
equivalent, so the detail screen was the single thing that would have kept `ui` from ever
compiling for iOS, desktop or web. Converting it before migrating the module — rather than
migrating around it with an `expect`/`actual` UI — keeps `ui` a single common implementation
instead of permanently forking one of its two screens.

The conversion is faithful rather than a redesign: the same 24dp padding, the same 56sp flag and
bold 28sp name, the same field order, `ScrollView` → `verticalScroll`, `LinearLayout` → `Column`,
each `TextView` → a `Text`. Hard-coded sizes map onto `MaterialTheme.typography` where they line
up exactly (`headlineMedium` is 28sp, `bodyLarge` is 16sp).

The payoff is bigger than one file: **`ui` now has no `android.*` imports at all**, and
`viewBinding` is off. What is left tying it to Android is resources — `stringResource`,
`painterResource`, and vector drawables that tint with `?attr/colorControlNormal` (an appcompat
attribute, which is why `androidx.appcompat` is still a dependency even though no Kotlin code
touches it). Those become Compose Multiplatform resources when the module migrates.

## Why are there two Metro graphs?

The graph originally lived in `app`, which is fine for exactly one Android app and wrong for
everything after that. The plan is three iOS-capable front ends: an Android app, a Compose
Multiplatform app, and a **SwiftUI/UIKit** app that drives Circuit presenters natively (the shape
of Circuit's own counter sample). A graph stuck in an Android application module cannot serve any
of the others.

The instinct is a single `shared` module, and that turns out not to work — for two reasons that
only surface when you try it.

**The SwiftUI app must not link Compose.** It never needs a `Circuit` instance or a `Ui.Factory`:
it instantiates a `Presenter`, wraps `present()` in Molecule, and observes a `StateFlow` from
Swift. Giving it a graph that exposes `Circuit` would drag Compose Multiplatform into a binary
that never renders a Composable.

**Metro aggregates contributions at the graph's compile classpath**, not at the app's. Hints are
generated into `metro.hints` and resolved during graph supertype generation, so a graph compiled
without `ui` visible cannot pick up its `Ui.Factory` multibindings later — an app module adding
`ui` downstream is too late. That rules out "one graph in `shared`, apps add their own UI module".

Hence two graphs, split exactly along the line that actually differs:

- `shared` → `CoreGraph`: repositories, Apollo, the root logger. No Compose. This is what the
  SwiftUI app and its iOS framework consume. It is KMP across all seven targets — and Metro does
  generate graphs for native and wasm, which was the main risk in the design.
- `shared-compose` → `ComposeGraph`: adds `presenter` and `ui`, exposes `Circuit`. Every Compose
  consumer shares this one declaration rather than repeating it — Android now, CMP iOS, desktop
  and web later.

`provideCircuit` moved out of the graph module and into `ui` as a contributed `CircuitProviders`,
which is where it belongs: assembling a `Circuit` from UI factories only means anything where
Compose UI exists. `app` is now purely an Android entry point — an Activity, a theme and a
manifest — with no `@Provides` of its own.

One sharp edge worth recording: contributing modules have to be `api` on a graph module, never
`implementation`. Contributed interfaces become *supertypes* of the generated graph, so
consumers need to see them too; `implementation` compiles the graph fine and then fails at the
consumer with `Cannot access 'NetworkProviders' which is a supertype of 'ComposeGraph'`.

`shared-compose` was an Android library at the time, purely because `presenter` and `ui` were. It
flipped to `kmp-library` when they migrated, and the browser app below is the first thing to take
the graph up on it.

## How is the browser app put together?

`:web` is one module targeting **both** `js` and `wasmJs`, and it is the counterpart to `:app`:
build the Metro graph, hand its `Circuit` to the app, and nothing else.

**The root composable had to move first.** The backstack, `CircuitCompositionLocals` and
`NavigableCircuitContent` were inline in `MainActivity`, which is fine for exactly one entry
point. They are now `CountriesApp` in `:ui`, and both entry points are three lines. The one thing
that stayed per-platform is `onRootPop`: Circuit's common `rememberCircuitNavigator` has no
default for it, only the Android overload does, and that turns out to be the right shape — Android
finishes the Activity, a browser tab has nothing to close. Passing `{ finish() }` explicitly is
more honest than inheriting it.

That left `CountriesApp` needing a `@Preview` under the project's own convention, and a `Circuit`
to preview it with. `previewCircuit()` in `PreviewSupport.kt` builds one from the real UIs and
fake presenters over the fixtures that were already there — which is worth more than satisfying
the convention: it is the only preview that exercises a screen through the Circuit machinery
rather than by calling the `Ui` function directly.

**Not `kmp-library`.** That convention is for libraries. It adds android, jvm, ios and macos
targets an app module has no use for, and it never calls `binaries.executable()` — the thing that
turns a klib into a bundle. Fourteen lines of `kotlin { }` inline beat a third convention plugin
for one module.

**`commonMain` is the web source set.** With only js and wasmJs on the module, the metadata
compilation already resolves `kotlinx-browser` and `org.w3c.dom`, so `window`, `history` and DOM
events are usable from common code with no `expect`/`actual` — the same thing Compose
Multiplatform does in its own `webMain`. The history binding, which is the most platform-flavoured
code in the project, is a single common file. `index.html` lands in `commonMain/resources` and
both distributions pick it up.

**Browser history is hand-written, because nothing offers it.** Circuit has no web history
integration, and Compose Multiplatform's web `BackHandler` runs off a `NavigationEventDispatcher`
that browser `popstate` does not feed. `BrowserHistory.kt` binds both directions — pushes become
`pushState`, in-app pops become `history.back()` so the forward button keeps meaning something,
and `popstate` drives the backstack — with two flags to stop the two directions from chasing each
other. Routes are hashes (`#/`, `#/country/FR`) because a static bundle has no server to rewrite
paths back to `index.html`. Deep links work: the backstack is seeded root-first from the URL, so
back from a shared country link goes to the list rather than out of the app.

**Verified by driving a real browser, not by a green build** — the habit the `:ui` migration
argued for, and it paid twice. Once on the app itself: list, detail, deep link, browser
back/forward, in-app back, composeResources, no console errors, on both js and wasmJs. And once
on the SQL.js cache, whose actual behaviour is recorded above and is not what the code claimed.

It did not pay a third time: every flag and every non-Latin name was rendering as tofu in those
same screenshots, and I attributed it to the test environment instead of the app. See the fonts
section below.

## Why did fonts force a Compose upgrade?

The browser app shipped rendering every flag and every non-Latin native name as a tofu box.

**Skia has no system font manager in a browser.** Compose rasterises text into a canvas, so the
browser's own fonts are unreachable from it; a codepoint with no glyph in a font Skia has been
*handed* has no glyph at all. Android, desktop and iOS never show this because those platforms
expose a system fallback. It is invisible until you run the web target, and it is not subtle once
you do: flags on all 250 list rows, plus 53 countries whose `native` name needs one of 14 scripts —
Arabic, Cyrillic, Greek, CJK, Georgian, Ethiopic, Thai, Armenian, Devanagari, Hangul, Lao, Hebrew,
Myanmar.

I got the first diagnosis wrong, and it is worth recording why. The tofu was visible in the
screenshots taken while verifying the web module, and I wrote it off as headless Chrome lacking
emoji fonts. That explanation is superficially reasonable and completely wrong — the browser's font
inventory has no bearing on what Skia can draw. It survived because it was plausible and because
nothing contradicted it; a bug was shipped as a rendering artifact of the test environment. The
lesson is narrower than "verify more": it is that an explanation which happens to *predict* the
observation is not the same as one that has been *checked*.

**The fix was a version bump, not code.** Compose Multiplatform 1.12.0-alpha02 added automatic
fallback-font loading on web. `ComposeWindow` calls `installFallbackFontDownloader()`
unconditionally; Skia reports unresolved codepoints during layout, the downloader batches them,
fetches the matching Noto woff2 subsets from `fonts.gstatic.com`, preloads them and forces a
re-layout. Confirmed in a browser on both targets: it pulls Noto Color Emoji chunk 0 — precisely
the flag block `U+1f1e6-1f1ff` — plus `notosansarabic`, `notosanshk`, `notosanskr` and
`notosansgeorgian`, and it picks the CJK variant from `navigator.language`. Cyrillic and Greek
needed nothing; the built-in font already covers them.

**The alternative was rejected on the numbers.** Bundling fallback fonts and preloading them by
hand is what the 1.11-era docs describe. It means ~1.15 MB for the flag chunk plus ~2 MB of
per-script subsets committed to the repo, and — because `FontCache` is per-resolver in 1.11 — a
rebuilt `FontFamily.Resolver`, re-preloaded with the whole accumulated set, every time a font
arrives after first paint. All of it deleted on the next Compose upgrade.

**What the upgrade costs, since it is not free.** The whole project moves onto prerelease Compose,
Android included: CMP 1.11.1 → 1.12.0-beta03, AndroidX Compose UI 1.11.4 stable → 1.12.0-rc01. The
web app gains an unconditional runtime dependency on a Google CDN with no opt-out, so non-Latin
text will not render offline until those files are cached. And CMP 1.12 added
`checkComposeUiTestConfiguration`, which hard-fails any Compose module whose browser test bundle
reaches skiko without an executable binary — `:presenter` and `:ui` each needed
`binaries.executable()` on their web targets, even though `:ui` has no tests and `:presenter`'s
never render.

Android was re-verified on an emulator rather than assumed, since the Compose jump from stable to
rc is the part of this change least exercised by the test suite and easiest to skip.

## What tradeoffs did I make due to time constraints?

- Minimal error handling/presentation (generic messages, swallowed cache misses).
- Normalized caching (memory → SQLite) was added but not deeply tuned; first launch still
  hits the network, and cache hits are per-exact-filter. On web the SQLite tier does not
  persist at all — see the SQL.js note above.
- ~~Tests focus on the presenter; mapping and the query builder are untested.~~ Mapping is now
  covered (`repository/src/commonTest`, running on every platform runner). The query builder
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

*(The XML → Compose migration is now done — see "Why is the detail screen no longer XML?" below.)*

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
the detail screen renders (an XML layout through `AndroidView` at the time; Compose since),
navigation and the back stack work — and by running the unit test suite.



