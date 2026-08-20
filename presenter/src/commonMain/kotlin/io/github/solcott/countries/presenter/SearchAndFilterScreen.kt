package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.TextFieldState
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import dev.zacsweers.redacted.annotations.Redacted
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.uistate.ContentState

/**
 * The search box and continent filter above the country list — a [SubScreen] rather than a
 * [com.slack.circuit.runtime.screen.Screen] because it is never a navigation destination.
 *
 * It owns the filter outright: the search text, the continent list, and which continents are
 * picked. [CountryListScreen] cannot read any of that — `SubCircuitContent` returns `Unit`, so a
 * sub-circuit's only channel to its host is [OuterEvent] — which is why this reports the whole
 * filter upward through [OuterEvent.FilterChanged] and the list presenter keeps a mirror of it.
 *
 * No `@Parcelize`, deliberately: unlike a `Screen`, a [SubScreen] is never put on a back stack and
 * Circuit does not require it to be saveable. The state below is kept across configuration changes
 * by `rememberSaveable` inside the presenter instead.
 *
 * A `data object` and not a `data class`: `SubCircuitContent` keys both its presenter lookup and
 * its `key()` block on the screen instance, so a screen carrying changing data would re-resolve the
 * presenter and discard its state on every keystroke.
 */
data object SearchAndFilterScreen : SubScreen<SearchAndFilterScreen.OuterEvent> {

  data class State(
    val nameStartsWithText: TextFieldState,
    val continentsState: ContentState<List<Continent>>,
    val selectedContinents: List<Continent>,
    /**
     * Whether the continent menu is open.
     *
     * Presenter state rather than a `remember` in the UI, so that the one thing this sub-circuit
     * does beyond holding the filter is testable without rendering anything.
     */
    val continentDropdownExpanded: Boolean,
    @Redacted val eventSink: (Event) -> Unit,
  ) : SubCircuitUiState

  sealed interface Event : SubCircuitUiEvent {
    data class ContinentToggled(val continent: Continent) : Event

    data class DropdownExpandedChanged(val expanded: Boolean) : Event

    /**
     * Sets the search text.
     *
     * Compose UIs do not send this — they bind [State.nameStartsWithText] directly, which is the
     * whole point of a [TextFieldState]. It exists for hosts that cannot: a `TextFieldState` is
     * snapshot-backed Compose Foundation state with no meaning outside a composition, so the
     * SwiftUI app owns a plain `String` and sends it here instead.
     */
    data class SearchTextChanged(val text: String) : Event
  }

  /**
   * What the host is told, and the only thing it is told.
   *
   * One event carrying the whole filter rather than one per control: the host's only use for it is
   * to re-run a query, and a single value means the mirror on the other side cannot drift out of
   * step with what is on screen here.
   */
  sealed interface OuterEvent : SubCircuitOuterEvent {
    data class FilterChanged(val name: String, val continents: List<Continent>) : OuterEvent
  }
}
