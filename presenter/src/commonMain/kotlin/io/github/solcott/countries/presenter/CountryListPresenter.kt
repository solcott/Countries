package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.runtime.Navigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.countries.uistate.reloading
import io.github.solcott.countries.uistate.settled
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

@OptIn(FlowPreview::class)
@CircuitInject(CountryListScreen::class, AppScope::class)
@Inject
@Composable
fun CountryListPresenter(
  navigator: Navigator,
  repository: CountryRepository,
  continentRepository: ContinentRepository,
): CountryListScreen.State {
  var reloadKey by retain { mutableIntStateOf(0) }
  val nameStartsWithText = rememberTextFieldState()
  val selectedContinents: SnapshotStateList<Continent> = rememberSaveable { mutableStateListOf() }
  val countriesState by
    produceRetainedState(
      initialValue = ContentState(data = emptyList()),
      key1 = reloadKey,
    ) {
      combine(
          snapshotFlow { nameStartsWithText.text }.debounce(300.milliseconds),
          snapshotFlow { selectedContinents.toList() },
        ) { name, continents ->
          name to continents
        }
        .collectLatest { (name, continents) ->
          // A new filter starts a fresh request: keep the current list visible but flag loading.
          value = value.reloading()
          repository
            .countriesAsFlow(name.toString(), continents.map { it.code })
            .distinctUntilChanged()
            .onEach { value = value.applyEmission(it) }
            .onCompletion { cause -> if (cause == null) value = value.settled() }
            .collect()
        }
    }
  val continentsState by
    produceRetainedState(
      initialValue = ContentState(data = emptyList()),
      key1 = reloadKey,
    ) {
      continentRepository
        .continentsAsFlow()
        .distinctUntilChanged()
        .onEach { value = value.applyEmission(it) }
        .onCompletion { cause -> if (cause == null) value = value.settled() }
        .collect()
    }

  fun handle(event: CountryListScreen.Event) {
    when (event) {
      is CountryListScreen.Event.CountryClicked -> navigator.goTo(CountryDetailScreen(event.code))
      CountryListScreen.Event.Retry -> reloadKey++
      is CountryListScreen.Event.SearchTextChanged ->
        nameStartsWithText.setTextAndPlaceCursorAtEnd(event.text)
      is CountryListScreen.Event.ToggleContinentSelection -> {
        if (selectedContinents.contains(event.continent)) {
          selectedContinents.remove(event.continent)
        } else {
          selectedContinents.add(event.continent)
        }
      }
    }
  }
  return CountryListScreen.State(
    nameStartsWithText = nameStartsWithText,
    countriesState = countriesState,
    continentsState = continentsState,
    selectedContinents = selectedContinents,
    eventSink = ::handle,
  )
}
