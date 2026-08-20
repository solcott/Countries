package io.github.solcott.countries.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.runtime.Navigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.countries.model.Continent
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

/**
 * What the user is filtering the list by.
 *
 * A mirror, not the source of truth — [SearchAndFilterScreen] owns the controls and reports every
 * change through [CountryListScreen.Event.FilterChanged]. A single immutable value rather than two
 * fields kept side by side, so the copy on this side can only ever be a whole filter the user
 * actually had on screen.
 */
private data class SearchFilter(
  val name: String = "",
  val continents: List<Continent> = emptyList(),
)

@OptIn(FlowPreview::class)
@CircuitInject(CountryListScreen::class, AppScope::class)
@Inject
@Composable
fun CountryListPresenter(
  navigator: Navigator,
  repository: CountryRepository,
): CountryListScreen.State {
  var reloadKey by retain { mutableIntStateOf(0) }
  var filter by retain { mutableStateOf(SearchFilter()) }
  val countriesState by
    produceRetainedState(
      initialValue = ContentState(data = emptyList()),
      key1 = reloadKey,
    ) {
      combine(
          snapshotFlow { filter.name }.debounce(300.milliseconds),
          snapshotFlow { filter.continents },
        ) { name, continents ->
          name to continents
        }
        // The sub-circuit re-reports its filter whenever it is composed — including after a
        // configuration change, where `rememberSaveable` restores it to what the mirror already
        // holds. Without this that echo would restart an identical query.
        .distinctUntilChanged()
        .collectLatest { (name, continents) ->
          // A new filter starts a fresh request: keep the current list visible but flag loading.
          value = value.reloading()
          repository
            .countriesAsFlow(name, continents.map { it.code })
            .distinctUntilChanged()
            .onEach { value = value.applyEmission(it) }
            .onCompletion { cause -> if (cause == null) value = value.settled() }
            .collect()
        }
    }

  fun handle(event: CountryListScreen.Event) {
    when (event) {
      is CountryListScreen.Event.CountryClicked -> selectCountry(navigator, event.code)
      is CountryListScreen.Event.FilterChanged ->
        filter = SearchFilter(event.name, event.continents)
      CountryListScreen.Event.Retry -> reloadKey++
    }
  }
  return CountryListScreen.State(
    countriesState = countriesState,
    // Reading the back stack rather than holding a copy. On the Compose side `peek()` reads a
    // SnapshotStateList, so this recomposes when the detail opens or closes.
    selectedCountryCode = (navigator.peek() as? CountryDetailScreen)?.code,
    eventSink = ::handle,
  )
}

/**
 * Opens [code] on the detail screen, *replacing* any country already open there.
 *
 * Replace rather than push, because in the two-pane layout the list is still on screen and still
 * clickable while a detail is showing. A plain `goTo` would grow the stack to `[list, detailA,
 * detailB]`: a stranded record, a back button that goes to the wrong country, and a browser history
 * entry per click. Selecting is not navigating deeper — it is the same move SwiftUI's
 * `NavigationSplitView` makes when its `selection` binding changes.
 *
 * The rule is layout-independent, which is why it lives here rather than in `:ui`. In the stacked
 * layout the list is never visible while a detail is on top, so [Navigator.peek] is always the list
 * screen and this collapses to the plain `goTo` it used to be.
 *
 * A single `if`, not a pop-until loop: `:apple`'s `SwiftNavigator.pop()` only forwards to Swift and
 * does not update its own mirrored stack — only `syncFromSwift` does — so the loop's condition
 * would never change.
 */
private fun selectCountry(navigator: Navigator, code: String) {
  val top = navigator.peek()
  if (top !is CountryDetailScreen) {
    navigator.goTo(CountryDetailScreen(code))
  } else if (top.code != code) {
    // One atomic move, so `:web`'s history binding sees a same-depth route change and rewrites the
    // current entry, rather than a 2 -> 1 -> 2 flicker it would read as a pop and then a push.
    Snapshot.withMutableSnapshot {
      navigator.pop()
      navigator.goTo(CountryDetailScreen(code))
    }
  }
  // Same country already showing: re-tapping the selected row does nothing.
}
