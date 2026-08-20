package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubPresenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.countries.uistate.settled
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Owns the country list's filter and the continents it can filter by.
 *
 * A class rather than a `@Composable` function, which is what [SubPresenter] requires — SubCircuit
 * has no `presenterOf` equivalent, so an annotated *function* is always taken to be a UI. Metro's
 * Circuit codegen turns the annotation below into a `SubPresenterFactory` contributed to
 * `Set<SubPresenterFactory>`, the same way it already does for `Presenter.Factory`;
 * `CircuitProviders` assembles them.
 */
@SubCircuitInject(SearchAndFilterScreen::class, AppScope::class)
@Inject
class SearchAndFilterPresenter(private val continentRepository: ContinentRepository) :
  SubPresenter<SearchAndFilterScreen.OuterEvent, SearchAndFilterScreen.State> {

  @Composable
  override fun present(
    outerEventSink: (SearchAndFilterScreen.OuterEvent) -> Unit
  ): SearchAndFilterScreen.State {
    val nameStartsWithText = rememberTextFieldState()
    val selectedContinents: SnapshotStateList<Continent> = rememberSaveable { mutableStateListOf() }
    var continentDropdownExpanded by remember { mutableStateOf(false) }
    // No reload key, unlike the country query this used to sit beside. Retry is offered only when
    // the country list failed with nothing cached, and in that state `CountryListUi` renders an
    // error instead of the list — so this presenter is not composed at all. Retrying puts the list
    // on screen, which composes the header for the first time and runs this fresh.
    val continentsState by
      produceRetainedState(initialValue = ContentState(data = emptyList())) {
        continentRepository
          .continentsAsFlow()
          .distinctUntilChanged()
          .onEach { value = value.applyEmission(it) }
          .onCompletion { cause -> if (cause == null) value = value.settled() }
          .collect()
      }

    // Read out rather than passed straight to the effect, so both are snapshot reads of *this*
    // composition — and so `toList()` copies the SnapshotStateList before it is compared. Its own
    // equality is identity-based on native and Kotlin/JS, which would make the effect key useless
    // on four of the six targets.
    val name = nameStartsWithText.text.toString()
    val continents = selectedContinents.toList()
    // The whole upward channel. Fires once on first composition with the empty filter — the same
    // first query the host used to make for itself — and once per change after that. Debouncing
    // stays with the host, so typing is still smoothed while a continent toggle still lands
    // immediately.
    LaunchedEffect(name, continents) {
      outerEventSink(SearchAndFilterScreen.OuterEvent.FilterChanged(name, continents))
    }

    fun handle(event: SearchAndFilterScreen.Event) {
      when (event) {
        is SearchAndFilterScreen.Event.ContinentToggled -> {
          if (selectedContinents.contains(event.continent)) {
            selectedContinents.remove(event.continent)
          } else {
            selectedContinents.add(event.continent)
          }
        }
        is SearchAndFilterScreen.Event.DropdownExpandedChanged ->
          continentDropdownExpanded = event.expanded
        is SearchAndFilterScreen.Event.SearchTextChanged ->
          nameStartsWithText.setTextAndPlaceCursorAtEnd(event.text)
      }
    }

    return SearchAndFilterScreen.State(
      nameStartsWithText = nameStartsWithText,
      continentsState = continentsState,
      selectedContinents = selectedContinents,
      continentDropdownExpanded = continentDropdownExpanded,
      eventSink = ::handle,
    )
  }
}
