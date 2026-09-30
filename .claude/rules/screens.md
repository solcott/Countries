---
paths:
  - "presenter/**"
---

# `:presenter` — Screens, presenters, state

- **A `Screen` carries `@CircuitSerializable(AppScope::class)`, not `@Parcelize`.** It is a
  `@MetaSerializable`, so do not also write `@Serializable`. Every property must be serializable —
  keep screens to the ids a presenter needs to re-fetch (`CountryDetailScreen(val code: String)`).
  **Forgetting it is not a build failure**: the throw lands the first time the screen is saved
  (rotation, process death, hot reload). **Add every new screen to `ComposeGraphSaverTest`** in
  `:shared-compose`.
- **`@Parcelize` from `io.github.solcott.kmp.parcelize` is still right for state** held in
  `rememberSaveable` (e.g. `Continent`). Never `org.jetbrains.kotlin.plugin.parcelize`.
- **`@Redacted` on `eventSink`.**
- **Collect repository flows with `produceRetainedContentState`** (`libs.uistateCircuit`). Do not
  hand-roll a `produceRetainedState` fold — the helper's `settled()` safety net is what stops a
  cancelled collection reporting an abandoned request as finished. `distinctUntilChanged()` stays
  with the caller. For parameters that change on screen (search, filter), use
  `params.produceRetainedContentState(…)` with the debounce on `params` — `CountryListPresenter` is
  the example.
- **There is no factory to register.** `@CircuitInject` + `metro.enableCircuitCodegen=true` generate
  it. If a screen does not resolve, suspect the annotation or the module's place on the graph
  classpath.
- **Presenters own state** — business logic and data access live here, never in the Ui. `presenter`
  must never depend on `ui`.
- View state types come from `libs.uistate`, read outcomes from `libs.dataresult`, domain nouns from
  `:model`. `when` over `Outcome` must handle `Loading`, though this project never emits it.
- A screen-specific derived property is an extension beside the state
  (`ContentState<CountryDetail?>.isNotFound`).

## Verify

`./gradlew :presenter:allTests :shared-compose:allTests`. The second one is the only check that a
`@CircuitSerializable` registration actually reached the graph.
