package io.github.solcott.countries.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.runtime.Navigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.repository.CountryRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

@CircuitInject(CountryDetailScreen::class, AppScope::class)
@Inject
@Composable
fun CountryDetailPresenter(
  screen: CountryDetailScreen,
  navigator: Navigator,
  repository: CountryRepository,
): CountryDetailScreen.State {
  var reloadKey by retain { mutableIntStateOf(0) }
  val content by
    produceRetainedState(
      initialValue = ContentState<CountryDetail?>(data = null),
      key1 = screen.code,
      reloadKey,
    ) {
      repository
        .countryAsFlow(screen.code)
        .distinctUntilChanged()
        .onEach { value = value.applyEmission(it) }
        .onCompletion { cause -> if (cause == null) value = value.settled() }
        .collect()
    }

  fun handle(event: CountryDetailScreen.Event) {
    when (event) {
      CountryDetailScreen.Event.BackClicked -> navigator.pop()
      CountryDetailScreen.Event.Retry -> reloadKey++
    }
  }

  return CountryDetailScreen.State(content = content, eventSink = ::handle)
}
