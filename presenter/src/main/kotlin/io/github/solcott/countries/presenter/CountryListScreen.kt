package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.TextFieldState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.redacted.annotations.Redacted
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import kotlinx.parcelize.Parcelize

@Parcelize
data object CountryListScreen : Screen {

  data class State(
      val nameStartsWithText: TextFieldState,
      val countriesState: ContentState<List<Country>>,
      val continentsState: ContentState<List<Continent>>,
      val selectedContinents: List<Continent>,
      @Redacted val eventSink: (Event) -> Unit,
  ) : CircuitUiState

  sealed interface Event : CircuitUiEvent {
    data class CountryClicked(val code: String) : Event

    data class ToggleContinentSelection(val continent: Continent) : Event

    data object Retry : Event
  }
}
