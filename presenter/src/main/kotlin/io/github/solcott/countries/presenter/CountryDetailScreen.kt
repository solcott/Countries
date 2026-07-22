package io.github.solcott.countries.presenter

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.redacted.annotations.Redacted
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import kotlinx.parcelize.Parcelize

@Parcelize
data class CountryDetailScreen(val code: String) : Screen {

    data class State(
        val isLoading: Boolean = true,
        val country: CountryDetail? = null,
        val countryNotFound: Boolean = false,
        val isError: Boolean = false,
        val errorMessage: String? = null,
        @Redacted
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object BackClicked : Event

        data object Retry : Event
    }
}
