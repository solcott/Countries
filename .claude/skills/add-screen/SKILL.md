---
name: add-screen
description: End-to-end recipe for adding a Circuit screen to this project — Screen, state and events in :presenter, the Ui in :ui, strings and drawables through composeResources, previews, and a presenter test. Use when adding a new screen or a new destination, or when you need the shape of an existing one to copy. Covers the wiring that is generated rather than written, and the resource rules that fail at runtime rather than at build time.
---

# Adding a screen

Two screens exist today: `CountryListScreen` and `CountryDetailScreen`. **Copy the closer of the
two rather than working from this file alone** — it names the moving parts, but the existing pair is
the real reference. See `AGENTS.md` for the module map and `compose-previews` for the preview
annotations.

## 1. Screen, state and events — `:presenter`

One file per screen, `presenter/src/commonMain/kotlin/io/github/solcott/countries/presenter/<Name>Screen.kt`:

```kotlin
@CircuitSerializable(AppScope::class)
data class ThingScreen(val id: String) : Screen {

  data class State(
    val content: ContentState<Thing?> = ContentState(data = null),
    @Redacted val eventSink: (Event) -> Unit,
  ) : CircuitUiState

  sealed interface Event : CircuitUiEvent {
    data object BackClicked : Event
    data object Retry : Event
  }
}
```

- **`@CircuitSerializable(AppScope::class)`**, from `com.slack.circuit.serialization`, is how a
  `Screen` survives being saved — Android process death, and `rememberSaveable` everywhere else.
  Circuit 0.38 moved this off `Parcelable`: `SerializableCircuitSaver` in
  `:ui`'s `CircuitProviders` is built from a `Set<CircuitSerializerRegistration>` multibinding, and
  Metro's Circuit codegen contributes one registration per annotated screen. There is nothing to
  register by hand — same deal as `@CircuitInject`.
  - The annotation is a `@MetaSerializable`, so it *implies* `@Serializable`. Do not write both.
    `:presenter` applies `alias(libs.plugins.kotlinx.serialization)`, which is what generates the
    serializer; a new module holding screens would have to apply it too.
  - Every property has to be serializable. Keep screens to the identifiers a presenter needs to
    re-fetch its data — `CountryDetailScreen(val code: String)` is the shape.
  - **Forgetting it is not a build failure.** The screen compiles, links and navigates; the
    registration set just comes up one short, and the throw lands the first time that screen is
    *saved* — a rotation, a process death, a desktop hot reload. `ComposeGraphSaverTest` in
    `:shared-compose`'s `commonTest` is what catches it, so **add the new screen there**; its
    `jvmTest` sibling `ComposeGraphSaverRoundTripTest` pins the restore half.
- **`@Parcelize`/`Parcelable` from `io.github.solcott.kmp.parcelize` is still right for state**, as
  opposed to screens — anything a presenter holds in `rememberSaveable` needs to be Bundle-able on
  Android. `model`'s `Continent` is the live example, kept saveable for
  `SearchAndFilterPresenter`'s `rememberSaveable { mutableStateListOf<Continent>() }`. That module
  applies `alias(libs.plugins.kmp.parcelize)`; it must **not** apply
  `org.jetbrains.kotlin.plugin.parcelize`, which does not work with the KMP Android plugin.
- **`@Redacted` on `eventSink`.** A lambda has no useful `toString()` and keeps state out of logs.
- View state comes from `libs.uistate` (`ContentState`, `LoadStatus`); read outcomes from `libs.dataresult`
  (`Outcome`, `DataError`, `Origin`); domain nouns from `:model`. Putting any of those in the wrong
  module breaks the Apple Swift export — `:model` and the two library artifacts are exported
  in full, and a Compose type or a generic sealed type in any of them fails the iOS build with
  nothing else warning you.
- A screen-specific derived property belongs next to the state, as an extension —
  `ContentState<CountryDetail?>.isNotFound` is the pattern.

## 2. The presenter — `:presenter`

`<Name>Presenter.kt` beside the screen:

```kotlin
@CircuitInject(ThingScreen::class, AppScope::class)
@Inject
@Composable
fun ThingPresenter(
  screen: ThingScreen,
  navigator: Navigator,
  repository: ThingRepository,
): ThingScreen.State {
  var reloadKey by retain { mutableIntStateOf(0) }
  val content = produceRetainedContentState<Thing?>(initial = null, screen.id, reloadKey) {
    repository.thingAsFlow(screen.id).distinctUntilChanged()
  }

  return ThingScreen.State(content) { event -> /* navigate, or bump reloadKey */ }
}
```

- **There is no factory to register.** `metro.enableCircuitCodegen=true` generates the
  `Presenter.Factory` multibinding from `@CircuitInject`. If a screen does not resolve at runtime,
  the annotation or the module's place on the graph classpath is the problem, not a missing
  registration.
- `retain` comes from `androidx.compose.runtime:runtime-retain` — multiplatform already, and with
  no Compose Multiplatform equivalent.
- **Presenters own state.** Business logic and data access live here; the Ui is a pure function of
  the state and emits events.
- **`produceRetainedContentState` collects the repository flow**, from `libs.uistateCircuit`
  (`io.github.solcott.uistate.circuit`). Do not hand-roll a `produceRetainedState` fold — the
  helper carries a `settled()` safety net guarded on `cause == null`, without which a cancelled
  collection reports an abandoned request as finished.
  - Its keys restart the producer, and the held value survives that — which is what keeps a retry
    from blanking the screen. It deliberately does *not* reset the status to loading on a restart.
  - `distinctUntilChanged()` on the repository flow stays with the caller; the helper does not
    dedupe what it is given.
  - For a source whose parameters change *while* it is on screen — a search term, a filter — use
    `params.produceRetainedContentState(initial, keys) { p -> … }` instead. It cancels the in-flight
    request per new parameter and marks the state reloading first, so content stays put under a
    refresh indicator. `CountryListPresenter` is the worked example; note the debounce belongs on
    the `params` flow, not in the helper.
- `applyEmission`, which the helper calls, comes from the `uistate` library
  (`io.github.solcott.uistate.applyEmission`) — it used to live in `presenter/ApplyEmission.kt`, and
  moved when the two modules were extracted into their own repo.
- **In a multi-pane layout, Circuit stops collecting for the record that is not current.**
  `pausableState` drops the producer from composition, so the list pane silently stops re-querying
  while a detail is open, with no error anywhere. `ui/.../ListDetailNavDecoration.kt` handles this
  with `ProvideRecordLifecycle(isActive = true)` around each composed pane — keep it.

## 3. The Ui — `:ui`

`ui/src/commonMain/kotlin/io/github/solcott/countries/ui/<Name>Ui.kt`:

```kotlin
@CircuitInject(ThingScreen::class, AppScope::class)
@Composable
fun ThingUi(state: ThingScreen.State, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize()) { … }
}
```

- **`modifier: Modifier = Modifier` is the first optional parameter, and it goes on the root
  element** — not on something nested. This holds for every private helper in the file too, and
  nothing in the build enforces it; the `compose-conventions` subagent is the check.
- **`:ui` has no `android.*` imports at all.** Keep it that way — there is no `AndroidView` escape
  hatch on five of the six platforms.
- The one platform seam in `:ui` is `LocalFlagFontFamily`, null everywhere but desktop. Do not add
  a second; see `compose-fonts`.
- Screen-agnostic wiring (theme, backstack, `CircuitCompositionLocals`, `NavigableCircuitContent`)
  belongs in `CountriesApp`, not in an entry point and not in your screen.

## 4. Strings and drawables

`ui/src/commonMain/composeResources/values/strings.xml` and `.../drawable/*.xml`, reached through
the generated `Res`, never AGP's `R`:

```kotlin
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.capital
import org.jetbrains.compose.resources.stringResource

stringResource(Res.string.capital, country.capital)
painterResource(Res.drawable.globe_24px)
```

`strings.xml` keeps the ordinary Android format, `%1$s` placeholders included.

**A vector drawable must contain no `?attr/…` theme attributes and no `@android:…` references.**
CMP's parser cannot resolve either, and **both fail at runtime rather than at build time.** Use
literal colours (`#FFFFFFFF`) and let `Icon` supply the real colour from `LocalContentColor`. The
existing drawables in that directory are all in the correct shape — copy one.

## 5. Previews — required, and not just the happy path

Every composable that emits UI needs a `@Preview`. Import
`androidx.compose.ui.tooling.preview.Preview`; use the project multipreviews from
`PreviewSupport.kt`:

- `@AppScreenPreviews` on the whole screen — the full device-size sweep, once, for the happy path.
- `@PreviewLightDark` at phone size for the other states.
- `@ComponentWidthPreviews` on a strip inside a screen.

**Preview the states that are easy to break: loading, loaded, error, empty.** Reuse the fixtures in
`PreviewSupport.kt` (`loadedState`, `loadingState`, `refreshingState`, `failedState`,
`PreviewSurface`, and the sample `Country`/`CountryDetail`) — the sample list deliberately includes
a country with a wrapping name and a null capital, which is what breaks a row first. Read
`compose-previews` before adding one.

## 6. Test the presenter — `:presenter`

`presenter/src/commonTest/kotlin/.../ThingPresenterTest.kt`, following
`CountryListPresenterTest`. JUnit is JVM-only — use `kotlin.test` assertions and Turbine, and add
`libs.kotlinx.coroutines.test` if you need `runTest`.

Two portability rules, because these tests run on six targets:

- **camelCase test names**, not backticked names with spaces.
- **`SnapshotStateList.equals` is structural on JVM/Android but identity-based on native and
  Kotlin/JS.** `assertEquals(listOf(x), someSnapshotStateList)` passes on JVM and fails everywhere
  else. Call `.toList()` first.

There is no Ui test layer in the Kotlin modules; UI behaviour is covered by the SwiftUI suites in
`iosApp/CountriesUITests` for the Apple app only.

## 7. Navigation

Add the destination to whatever navigates to it — usually a `navigator.goTo(ThingScreen(id))` from
another presenter's event sink. If the screen should be reachable by URL in the browser, add it to
`Routes.kt` in `:web` and to the precedence table in `historyAction()`; that function is pure and
tested, so **change the navigation rules there, not in `BrowserHistory`.** See `web-app`.

## 8. Verify

Run the `verify` skill to work out the task set. For a screen touching `:presenter` and `:ui` that
is at minimum:

```
./gradlew ktfmtFormat
./gradlew :presenter:allTests :shared-compose:allTests :ui:assemble assembleDebug
```

`:shared-compose:allTests` is the one that proves the screen's `@CircuitSerializable` registration
actually reached the graph — nothing else in the build notices a missing one.

Then look at it running on at least one platform — the `run` skill covers launching each app.
