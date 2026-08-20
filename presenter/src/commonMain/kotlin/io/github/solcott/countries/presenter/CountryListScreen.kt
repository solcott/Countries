package io.github.solcott.countries.presenter

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.redacted.annotations.Redacted
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.kmp.parcelize.Parcelize

@Parcelize
data object CountryListScreen : Screen {

  data class State(
    val countriesState: ContentState<List<Country>>,
    /**
     * The country the detail screen is currently showing, or null if it is not open.
     *
     * The list needs this only because of the two-pane layout: on a window wide enough to show
     * both, the list stays on screen beside the detail and has to mark which row that detail is
     * for. Derived from the navigator rather than stored, so the back stack stays the single source
     * of truth for what is selected.
     *
     * Always null on Apple: `SwiftNavigator` mirrors its stack in a plain `var` rather than
     * snapshot state, so a presenter reading it would not recompose. Harmless — SwiftUI's
     * `NavigationSplitView` owns its own selection and never reads this.
     */
    val selectedCountryCode: String? = null,
    @Redacted val eventSink: (Event) -> Unit,
  ) : CircuitUiState

  sealed interface Event : CircuitUiEvent {
    data class CountryClicked(val code: String) : Event

    /**
     * Reports what the user is filtering by.
     *
     * Sent by whoever hosts [SearchAndFilterScreen] — `CountryListUi` forwards the sub-circuit's
     * `OuterEvent.FilterChanged`, and the Apple bridge does the same from its own composition. That
     * sub-circuit owns the search text and the continent selection outright, so this event is the
     * only way this presenter learns about either.
     */
    data class FilterChanged(val name: String, val continents: List<Continent>) : Event

    data object Retry : Event
  }
}
