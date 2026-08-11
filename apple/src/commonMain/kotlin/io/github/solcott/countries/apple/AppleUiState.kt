package io.github.solcott.countries.apple

import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.LoadStatus

/**
 * The Swift-facing view state, and the reason it is not just `CountryListScreen.State`.
 *
 * Under SKIE the Circuit state crossed the boundary as-is. Swift export cannot take it, for two
 * independent reasons — both verified against the generated Swift rather than assumed:
 *
 * 1. **`TextFieldState`.** Compose Foundation's `Saver.save` is a method with an extension
 *    receiver, and the reverse-interop thunk generated for it passes the receiver as the `value:`
 *    argument and omits the receiver entirely. The generated Swift does not compile. Reaching
 *    `TextFieldState` also drags Compose ui-text, ui-graphics, ui-geometry and ui-unit into the
 *    export — roughly 8,000 lines of generated Swift for a property Swift can never use.
 * 2. **`ContentState<T>`.** Generic classes export with their type parameter erased, so `data`
 *    arrives as `any _KotlinBridgeable` and every read needs an unchecked cast. That was survivable
 *    — the Obj-C build already cast — but it is not something to keep once a facade exists anyway.
 *
 * So these types are deliberately dull: no generics, no Compose, no Circuit. What does cross is
 * `:model`'s own data classes and [LoadStatus], which is a plain non-generic sealed interface and
 * therefore exactly what 2.4.20-Beta2's sealed-enum support handles well — it reaches Swift as an
 * exhaustively switchable enum, which is the SKIE behaviour worth preserving.
 *
 * [eventSink] is `internal` on purpose. It is how the holder forwards Swift's method calls back
 * into Circuit, and keeping it off the public API keeps `CountryListScreen.Event` — and with it the
 * risk of pulling `CountryListScreen.State` and `TextFieldState` back in as a sibling nested type —
 * out of the export.
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
)

/** See [CountryListUiState]. */
class CountryDetailUiState
internal constructor(
  val country: CountryDetail?,
  val status: LoadStatus,
  internal val eventSink: (CountryDetailScreen.Event) -> Unit,
)

internal fun CountryListScreen.State.toUiState() =
  CountryListUiState(
    countries = countriesState.data,
    continents = continentsState.data,
    selectedContinents = selectedContinents,
    searchText = nameStartsWithText.text.toString(),
    countriesStatus = countriesState.status,
    continentsStatus = continentsState.status,
    eventSink = eventSink,
  )

internal fun CountryDetailScreen.State.toUiState() =
  CountryDetailUiState(country = content.data, status = content.status, eventSink = eventSink)
