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

## What tradeoffs did I make due to time constraints?

- Minimal error handling/presentation (generic messages, swallowed cache misses).
- Normalized caching (memory → SQLite) was added but not deeply tuned; first launch still
  hits the network, and cache hits are per-exact-filter.
- Tests focus on the presenter; mapping and the query builder are untested.

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



