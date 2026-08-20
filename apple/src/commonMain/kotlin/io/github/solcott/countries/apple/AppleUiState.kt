package io.github.solcott.countries.apple

import dev.zacsweers.redacted.annotations.Redacted
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.SearchAndFilterScreen
import io.github.solcott.countries.uistate.LoadStatus

/**
 * An event sink that no `equals` can tell apart from another.
 *
 * The state classes below are `data class`es so that `StateFlow` can conflate a Molecule frame that
 * changed nothing. Molecule builds one state per recomposition, and without value equality every
 * frame is a distinct instance `StateFlow` cannot dedupe — so `PresenterModel.state` reassigns and
 * SwiftUI invalidates the whole view on recompositions that produced identical state.
 *
 * A sink is a fresh lambda on every frame, so a generated `equals` that included one would never
 * report two frames equal and the conflation would never fire. Hence this wrapper: **any two sinks
 * compare equal**, which reduces the generated `equals` to the data fields — exactly what the
 * hand-written `equals` this replaces did, but without a body to forget to update when a field is
 * added.
 *
 * Conflating them is safe because a sink closes over objects that outlive the frame that made it:
 * the `remember`ed `TextFieldState` and `SnapshotStateList` in `SearchAndFilterPresenter`, and the
 * `retain`ed `MutableState`s and constructor-scoped `Navigator` in `CountryListPresenter` and
 * `CountryDetailPresenter`. An older sink therefore writes to exactly the same state a newer one
 * would, which matters because the holders read `state.value.eventSink` — and after conflation
 * `state.value` may be the older instance.
 *
 * "Everything is equal" is a lie a general-purpose type could not tell. It is sound here only
 * because this class is `internal` to this module, is held by nothing but the two states below, and
 * is never a key in a map or a set.
 *
 * [invoke] is an operator so a call site reads `state.eventSink(SomeEvent)` — identical to invoking
 * the raw lambda, so wrapping cost no call site a change.
 */
internal class EventSink<E>(private val send: (E) -> Unit) {

  operator fun invoke(event: E) = send(event)

  override fun equals(other: Any?) = other is EventSink<*>

  override fun hashCode() = 0
}

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
 * **A `data class` costs the export nothing, which is not what it looks like.** Swift export emits
 * `equals`, `hashCode`, `toString` and a static `==` for one — all additive — and emits **no**
 * `componentN()` at all, because it skips component operators. `copy()` it would emit, and that one
 * matters: its parameters would name [CountryListScreen.Event], pulling `CountryListScreen` and
 * with it `TextFieldState` back in as a sibling nested type. [ConsistentCopyVisibility] is what
 * stops it — `copy()` takes the primary constructor's visibility, the constructor is `internal`,
 * and internal members are not exported. Check `CountriesKit.swift` after changing any of this;
 * nothing else will tell you.
 *
 * The sinks are `internal` for the same reason, and are how the holder forwards Swift's method
 * calls back into Circuit. `@Redacted` keeps them out of the generated `toString()` — it does not
 * affect equality; [EventSink] does that.
 *
 * There are **two** sinks because the list is two presenters: `CountryListScreen` owns the
 * countries, and the `SearchAndFilterScreen` sub-circuit owns the filter. This class deliberately
 * flattens both into one object, so SwiftUI keeps seeing the single view state it always had — see
 * `CountryListPresenterHolder` for where the two are composed.
 */
@ConsistentCopyVisibility
data class CountryListUiState
internal constructor(
  val countries: List<Country>,
  val continents: List<Continent>,
  val selectedContinents: List<Continent>,
  /** Plain text rather than a `TextFieldState`: SwiftUI owns the search field and echoes back. */
  val searchText: String,
  val countriesStatus: LoadStatus,
  val continentsStatus: LoadStatus,
  @Redacted internal val eventSink: EventSink<CountryListScreen.Event>,
  @Redacted internal val headerEventSink: EventSink<SearchAndFilterScreen.Event>,
)

/** See [CountryListUiState]. */
@ConsistentCopyVisibility
data class CountryDetailUiState
internal constructor(
  val country: CountryDetail?,
  val status: LoadStatus,
  @Redacted internal val eventSink: EventSink<CountryDetailScreen.Event>,
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
    eventSink = EventSink(eventSink),
    headerEventSink = EventSink(header.eventSink),
  )

internal fun CountryDetailScreen.State.toUiState() =
  CountryDetailUiState(
    country = content.data,
    status = content.status,
    eventSink = EventSink(eventSink),
  )
