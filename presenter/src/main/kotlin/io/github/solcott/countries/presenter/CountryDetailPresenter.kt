package io.github.solcott.countries.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.runtime.Navigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.countries.model.Response
import io.github.solcott.countries.repository.CountryRepository
import kotlinx.coroutines.flow.distinctUntilChanged

@CircuitInject(CountryDetailScreen::class, AppScope::class)
@Inject
@Composable
fun CountryDetailPresenter(
    screen: CountryDetailScreen,
    navigator: Navigator,
    repository: CountryRepository,
): CountryDetailScreen.State {
  var reloadKey by retain { mutableIntStateOf(0) }
  val result by
  produceRetainedState(initialValue = Response.Loading(), key1 = screen.code, reloadKey) {
    repository.countryAsFlow(screen.code).distinctUntilChanged().collect {
      value = it
    }
  }

  fun handle(event: CountryDetailScreen.Event) {
    when (event) {
      CountryDetailScreen.Event.BackClicked -> navigator.pop()
      CountryDetailScreen.Event.Retry -> reloadKey++
    }
  }

    val isLoading = result.isLoading
    val isError = result.isError
    val country = (result as? Response.Data)?.data
    val countryNotFound = !isLoading && !isError && country == null
    val errorMessage = (result as? Response.Error)?.message
    return CountryDetailScreen.State(
      isLoading = isLoading,
      country = country,
      countryNotFound = countryNotFound,
      isError = isError,
      errorMessage = errorMessage,
      eventSink = ::handle,
  )
}
