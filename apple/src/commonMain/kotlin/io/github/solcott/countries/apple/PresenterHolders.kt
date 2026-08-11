package io.github.solcott.countries.apple

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import io.github.solcott.countries.presenter.CountryDetailPresenter
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListPresenter
import io.github.solcott.countries.presenter.CountryListScreen
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
 * coroutine scope and folds each emission into a [StateFlow], which SKIE in turn exposes to Swift
 * as an `AsyncSequence` with a non-optional `value`.
 *
 * Circuit's own iOS sample wraps the presenter in `presenterOf { }` first. That is unnecessary here
 * — [launchMolecule] already takes a `@Composable` lambda — and it would introduce a
 * `@ComposableTarget("presenter")` boundary inside a lambda Molecule infers as a UI target. Calling
 * the presenter function directly is what `CountryListPresenterTest` does too.
 *
 * **This class is deliberately not generic**, even though every subclass differs only in its state
 * type. A `PresenterHolder<UiState>` would reach Swift as an Objective-C lightweight generic and
 * force SKIE to express `SkieSwiftStateFlow<UiState>` for a type parameter; keeping `state`
 * declared concretely on each subclass is what makes it read as
 * `SkieSwiftStateFlow<CountryListScreen.State>` over there. Circuit's sample has the generic
 * version, and its own comments complain about the resulting Swift ergonomics. So the shared part
 * is a generic *method*, [moleculeState], rather than a type parameter.
 *
 * Construct subclasses through [CountriesCore], never directly, so Swift never assembles the object
 * graph by hand.
 */
abstract class PresenterHolder internal constructor(private val scope: CoroutineScope) {

  /** Recomposes [body] and folds every emission into a [StateFlow]. */
  // `body = body` is named, not positional: launchMolecule's second positional parameter is
  // `context` on one overload and `emitter` on another, so a bare second argument is ambiguous.
  protected fun <UiState : CircuitUiState> moleculeState(
    body: @Composable () -> UiState
  ): StateFlow<UiState> = scope.launchMolecule(mode = RecompositionMode.Immediate, body = body)

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

/** Runs `CountryListPresenter`. See [PresenterHolder]. */
class CountryListPresenterHolder
internal constructor(
  navigator: Navigator,
  countryRepository: CountryRepository,
  continentRepository: ContinentRepository,
  scope: CoroutineScope = MainScope(),
) : PresenterHolder(scope) {

  // Molecule's Immediate mode computes the first frame synchronously, so `value` holds a real state
  // before anything is awaited — no optional, and no empty first render.
  val state: StateFlow<CountryListScreen.State> = moleculeState {
    CountryListPresenter(navigator, countryRepository, continentRepository)
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

  val state: StateFlow<CountryDetailScreen.State> = moleculeState {
    CountryDetailPresenter(screen, navigator, countryRepository)
  }
}
