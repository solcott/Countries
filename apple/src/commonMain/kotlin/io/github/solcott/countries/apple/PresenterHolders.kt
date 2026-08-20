package io.github.solcott.countries.apple

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.slack.circuit.runtime.Navigator
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.presenter.CountryDetailPresenter
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListPresenter
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.SearchAndFilterPresenter
import io.github.solcott.countries.presenter.SearchAndFilterScreen
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

/**
 * Runs a Circuit presenter and exposes its state to Swift.
 *
 * The presenters are `@Composable` functions, which have a hidden `$composer` parameter and are
 * therefore not callable from Swift at all. Molecule is the bridge: it recomposes the function on a
 * coroutine scope and folds each emission into a [StateFlow], which Swift export exposes as
 * `KotlinTypedStateFlow<T>` — a typed, non-optional `value` plus `asAsyncSequence()`, and it needs
 * no help from this class to do it.
 *
 * Circuit's own iOS sample wraps the presenter in `presenterOf { }` first. That is unnecessary here
 * — [launchMolecule] already takes a `@Composable` lambda — and it would introduce a
 * `@ComposableTarget("presenter")` boundary inside a lambda Molecule infers as a UI target. Calling
 * the presenter function directly is what `CountryListPresenterTest` does too.
 *
 * **This class is deliberately not generic**, even though every subclass differs only in its state
 * type. Swift export erases a generic type parameter to its upper bound, so a
 * `PresenterHolder<UiState>` would reach Swift having lost the very type Swift needs. The shared
 * part is therefore a generic *method*, [moleculeState], rather than a type parameter, and each
 * subclass declares `state` concretely. Circuit's own iOS sample has the generic version, and its
 * comments complain about the resulting Swift ergonomics.
 *
 * Subclasses publish [CountryListUiState]/[CountryDetailUiState] rather than the Circuit state, and
 * expose named methods rather than an `eventSink`. See [CountryListUiState] for why.
 *
 * Construct subclasses through [CountriesCore], never directly, so Swift never assembles the object
 * graph by hand.
 */
abstract class PresenterHolder internal constructor(private val scope: CoroutineScope) {

  /**
   * Recomposes [body] and folds every emission into a [StateFlow].
   *
   * The type parameter is no longer bounded by `CircuitUiState`: [body] now maps the Circuit state
   * to a plain Apple-facing type before emitting, and those deliberately implement nothing.
   */
  // `body = body` is named, not positional: launchMolecule's second positional parameter is
  // `context` on one overload and `emitter` on another, so a bare second argument is ambiguous.
  protected fun <UiState> moleculeState(body: @Composable () -> UiState): StateFlow<UiState> =
    scope.launchMolecule(mode = RecompositionMode.Immediate, body = body)

  /**
   * Stops the presenter. Call when the owning view goes away.
   *
   * Circuit's iOS sample leaks its scope. That is harmless for a one-screen counter and wrong here,
   * where a detail holder is created once per country the user opens.
   */
  fun cancel() {
    scope.cancel()
  }
}

/**
 * Runs `CountryListPresenter` and the `SearchAndFilterScreen` sub-circuit. See [PresenterHolder].
 */
class CountryListPresenterHolder
internal constructor(
  navigator: Navigator,
  countryRepository: CountryRepository,
  continentRepository: ContinentRepository,
  scope: CoroutineScope = MainScope(),
) : PresenterHolder(scope) {

  /**
   * Both presenters behind the country list, composed into one frame.
   *
   * The Compose apps get this wiring for free: `CountryListUi` embeds the header with
   * `SubCircuitContent`, which composes the sub-presenter and forwards its outer events. There is
   * no Compose UI here, so a `SubPresenter` would never run at all unless this does it — and
   * without it the SwiftUI app would lose its search field and its continent menu.
   *
   * Order matters. `listState` is composed first so its `eventSink` exists to close over; the sink
   * below is only *invoked* from the sub-presenter's `LaunchedEffect`, which runs after the
   * composition that created it.
   *
   * Molecule's Immediate mode computes the first frame synchronously, so `value` holds a real state
   * before anything is awaited — no optional, and no empty first render.
   */
  val state: StateFlow<CountryListUiState> = moleculeState {
    val listState = CountryListPresenter(navigator, countryRepository)
    val headerPresenter = remember { SearchAndFilterPresenter(continentRepository) }
    val headerState = headerPresenter.present { outerEvent ->
      when (outerEvent) {
        is SearchAndFilterScreen.OuterEvent.FilterChanged ->
          listState.eventSink(
            CountryListScreen.Event.FilterChanged(outerEvent.name, outerEvent.continents)
          )
      }
    }
    listState.toUiState(headerState)
  }

  /**
   * Sets the search text.
   *
   * Goes through `SearchTextChanged` rather than a shared `TextFieldState`, which is what that
   * event exists for: the Compose hosts bind the `TextFieldState` directly, and a host that is not
   * a composition cannot.
   *
   * Lands on the header's sink, not the list's: the sub-circuit owns the filter, and the list
   * presenter only ever hears the result of it.
   */
  fun search(text: String) {
    state.value.headerEventSink(SearchAndFilterScreen.Event.SearchTextChanged(text))
  }

  fun toggleContinent(continent: Continent) {
    state.value.headerEventSink(SearchAndFilterScreen.Event.ContinentToggled(continent))
  }

  fun retry() {
    state.value.eventSink(CountryListScreen.Event.Retry)
  }
}

/** Runs `CountryDetailPresenter` for one country code. See [PresenterHolder]. */
class CountryDetailPresenterHolder
internal constructor(
  screen: CountryDetailScreen,
  navigator: Navigator,
  countryRepository: CountryRepository,
  scope: CoroutineScope = MainScope(),
) : PresenterHolder(scope) {

  val state: StateFlow<CountryDetailUiState> = moleculeState {
    CountryDetailPresenter(screen, navigator, countryRepository).toUiState()
  }

  fun back() {
    state.value.eventSink(CountryDetailScreen.Event.BackClicked)
  }

  fun retry() {
    state.value.eventSink(CountryDetailScreen.Event.Retry)
  }
}
