package io.github.solcott.countries.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.repository.CountryRepository
import io.github.solcott.uistate.circuit.produceRetainedContentState
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
  val content =
    produceRetainedContentState<CountryDetail?>(initial = null, screen.code, reloadKey) {
      repository.countryAsFlow(screen.code).distinctUntilChanged()
    }

  fun handle(event: CountryDetailScreen.Event) {
    when (event) {
      CountryDetailScreen.Event.BackClicked -> navigator.pop()
      CountryDetailScreen.Event.Retry -> reloadKey++
    }
  }

  return CountryDetailScreen.State(content = content, eventSink = ::handle)
}
