package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.TextFieldState
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
    val nameStartsWithText: TextFieldState,
    val countriesState: ContentState<List<Country>>,
    val continentsState: ContentState<List<Continent>>,
    val selectedContinents: List<Continent>,
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

    data class ToggleContinentSelection(val continent: Continent) : Event

    /**
     * Sets the search text.
     *
     * Compose UIs do not send this — they bind [State.nameStartsWithText] directly, which is the
     * whole point of a [TextFieldState]. It exists for hosts that cannot: a `TextFieldState` is
     * snapshot-backed Compose Foundation state with no meaning outside a composition, so the
     * SwiftUI app owns a plain `String` and sends it here instead.
     */
    data class SearchTextChanged(val text: String) : Event

    data object Retry : Event
  }
}
