package io.github.solcott.countries.apple

import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.SearchAndFilterScreen
import io.github.solcott.countries.uistate.LoadStatus

/**
 * The Swift-facing view state, and the reason it is not just `CountryListScreen.State`.
 *
 * Swift export cannot carry `CountryListScreen.State` across, for two independent reasons — both
 * verified against the generated Swift rather than assumed:
 *
 * 1. **`TextFieldState`.** Compose Foundation's `Saver.save` is a method with an extension
 *    receiver, and the reverse-interop thunk generated for it passes the receiver as the `value:`
 *    argument and omits the receiver entirely. The generated Swift does not compile. Reaching
 *    `TextFieldState` also drags Compose ui-text, ui-graphics, ui-geometry and ui-unit into the
 *    export — roughly 8,000 lines of generated Swift for a property Swift can never use.
 * 2. **`ContentState<T>`.** Generic classes export with their type parameter erased, so `data`
 *    arrives as `any _KotlinBridgeable` and every read needs an unchecked cast — survivable, but
 *    not worth keeping once a facade exists anyway.
 *
 * So these types are deliberately dull: no generics, no Compose, no Circuit. What does cross is
 * `:model`'s own data classes and [LoadStatus], which is a plain non-generic sealed class and
 * therefore exactly what 2.4.20's sealed-type support handles well — it reaches Swift through a
 * generated `sealedType()` as an exhaustively switchable enum.
 *
 * [eventSink] is `internal` on purpose. It is how the holder forwards Swift's method calls back
 * into Circuit, and keeping it off the public API keeps `CountryListScreen.Event` — and with it the
 * risk of pulling `CountryListScreen.State` and `TextFieldState` back in as a sibling nested type —
 * out of the export.
 *
 * There are **two** sinks because the list is two presenters: `CountryListScreen` owns the
 * countries, and the `SearchAndFilterScreen` sub-circuit owns the filter. This class deliberately
 * flattens both into one object, so SwiftUI keeps seeing the single view state it always had — see
 * `CountryListPresenterHolder` for where the two are composed.
 */
class CountryListUiState
internal constructor(
  val countries: List<Country>,
  val continents: List<Continent>,
  val selectedContinents: List<Continent>,
  /** Plain text rather than a `TextFieldState`: SwiftUI owns the search field and echoes back. */
  val searchText: String,
  val countriesStatus: LoadStatus,
  val continentsStatus: LoadStatus,
  internal val eventSink: (CountryListScreen.Event) -> Unit,
  internal val headerEventSink: (SearchAndFilterScreen.Event) -> Unit,
) {

  /**
   * Value equality over the data, so `StateFlow` can conflate a frame that changed nothing.
   *
   * Molecule builds one of these per recomposition, and without this every frame is a distinct
   * instance that `StateFlow` cannot dedupe — so `PresenterModel.state` reassigns and SwiftUI
   * invalidates the whole list view on recompositions that produced identical state. Written out
   * rather than `data class`, which would put `copy()` and `componentN()` into the Swift export and
   * fold the two sinks into equality.
   *
   * **The sinks are deliberately excluded.** They are fresh lambdas each frame, so including them
   * would defeat the whole point. Conflating them is safe because both close over objects that
   * outlive the frame — the `remember`ed `TextFieldState` and `SnapshotStateList` in
   * `SearchAndFilterPresenter`, and the `retain`ed `MutableState`s in `CountryListPresenter` — so
   * an older sink writes to exactly the same state a newer one would.
   */
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is CountryListUiState &&
        countries == other.countries &&
        continents == other.continents &&
        selectedContinents == other.selectedContinents &&
        searchText == other.searchText &&
        countriesStatus == other.countriesStatus &&
        continentsStatus == other.continentsStatus)

  override fun hashCode(): Int {
    var result = countries.hashCode()
    result = 31 * result + continents.hashCode()
    result = 31 * result + selectedContinents.hashCode()
    result = 31 * result + searchText.hashCode()
    result = 31 * result + countriesStatus.hashCode()
    result = 31 * result + continentsStatus.hashCode()
    return result
  }
}

/** See [CountryListUiState]. */
class CountryDetailUiState
internal constructor(
  val country: CountryDetail?,
  val status: LoadStatus,
  internal val eventSink: (CountryDetailScreen.Event) -> Unit,
)

/**
 * Flattens the two presenters behind the country list into the one state Swift sees.
 *
 * The Compose apps never need this: there the sub-circuit renders itself and the list pane only
 * learns what the filter is set to. Here nothing renders, so both halves have to be read out and
 * merged by hand.
 */
internal fun CountryListScreen.State.toUiState(header: SearchAndFilterScreen.State) =
  CountryListUiState(
    countries = countriesState.data,
    continents = header.continentsState.data,
    // Copied, not passed through. `header.selectedContinents` is the sub-presenter's live
    // `SnapshotStateList`: Swift would read it on the main thread while Molecule mutates it in
    // composition, and its `equals` is identity-based on native, which would make the value
    // equality above never fire. `PresenterHolderTest` already had to call `.toList()` for the
    // same reason.
    selectedContinents = header.selectedContinents.toList(),
    searchText = header.nameStartsWithText.text.toString(),
    countriesStatus = countriesState.status,
    continentsStatus = header.continentsState.status,
    eventSink = eventSink,
    headerEventSink = header.eventSink,
  )

internal fun CountryDetailScreen.State.toUiState() =
  CountryDetailUiState(country = content.data, status = content.status, eventSink = eventSink)
