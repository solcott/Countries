package io.github.solcott.countries.presenter

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.redacted.annotations.Redacted
import io.github.solcott.countries.model.CountryDetail
import kotlinx.parcelize.Parcelize

@Parcelize
data class CountryDetailScreen(val code: String) : Screen {

  data class State(
      val content: ContentState<CountryDetail?> = ContentState(data = null),
      @Redacted val eventSink: (Event) -> Unit,
  ) : CircuitUiState

  sealed interface Event : CircuitUiEvent {
    data object BackClicked : Event

    data object Retry : Event
  }
}

/** A settled request that produced no country — the requested code does not exist. */
val ContentState<CountryDetail?>.isNotFound: Boolean
  get() = status is LoadStatus.Idle && data == null
