package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.rememberTextFieldState
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
import io.github.solcott.countries.model.Response
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformLatest

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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
  val countriesLoadState by
      produceRetainedState(initialValue = CountriesState(loading = true), key1 = reloadKey) {
        combine(
                snapshotFlow { nameStartsWithText.text }.debounce(300.milliseconds),
                snapshotFlow { selectedContinents.toList() },
            ) { nameStartsWithText, continents ->
              Pair(nameStartsWithText, continents)
            }
            .transformLatest { latest ->
                emitAll(
                  repository
                      .countriesAsFlow(latest.first.toString(), latest.second.map { it.code })
                      .distinctUntilChanged()
              )
            }
            .collect { countriesResponse ->
              value =
                  CountriesState(
                      loading = countriesResponse.isLoading,
                      data = (countriesResponse as? Response.Data)?.data.orEmpty(),
                      error = countriesResponse.isError,
                      errorMessage = (countriesResponse as? Response.Error)?.message,
                  )
            }
      }
  val continentsState by
      produceRetainedState(initialValue = ContinentsState(loading = true), key1 = reloadKey) {
        continentRepository.continentsAsFlow().distinctUntilChanged().collect {
          value =
              ContinentsState(
                  loading = it.isLoading,
                  data = (it as? Response.Data)?.data.orEmpty(),
                  error = it.isError,
                  errorMessage = (it as? Response.Error)?.message,
              )
        }
      }

  fun handle(event: CountryListScreen.Event) {
    when (event) {
      is CountryListScreen.Event.CountryClicked -> navigator.goTo(CountryDetailScreen(event.code))
      CountryListScreen.Event.Retry -> reloadKey++
      is CountryListScreen.Event.ToggleContinentSelection ->{
          if(selectedContinents.contains(event.continent)){
              selectedContinents.remove(event.continent)
          } else {
              selectedContinents.add(event.continent)
          }
      }
    }
  }
  return CountryListScreen.State(
      nameStartsWithText = nameStartsWithText,
      countriesState = countriesLoadState,
      continentsState = continentsState,
      selectedContinents = selectedContinents,
      eventSink = ::handle,
  )
}
