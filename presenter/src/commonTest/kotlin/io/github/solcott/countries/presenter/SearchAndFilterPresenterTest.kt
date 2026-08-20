package io.github.solcott.countries.presenter

import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.subcircuit.test.test
import io.github.solcott.countries.dataresult.DataError
import io.github.solcott.countries.dataresult.Origin
import io.github.solcott.countries.dataresult.Outcome
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.uistate.LoadStatus
import io.github.solcott.countries.uistate.errorOrNull
import io.github.solcott.countries.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class SearchAndFilterPresenterTest {

  private val africa = Continent(code = "AF", name = "Africa")
  private val europe = Continent(code = "EU", name = "Europe")
  private val oceania = Continent(code = "OC", name = "Oceania")
  private val continents = listOf(africa, europe, oceania)

  @Test
  fun loadsContinentsAndEmitsLoadedState() = runTest {
    val presenter =
      SearchAndFilterPresenter(
        FakeContinentRepository(continentsAsFlow = { flowOf(data(continents)) })
      )

    presenter.test {
      assertTrue(awaitItem().continentsState.isLoading)

      val loaded = awaitContinentsSettled()
      assertEquals(continents, loaded.continentsState.data)
      assertEquals(Origin.Network, loaded.continentsState.origin)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun surfacesContinentFailuresAsErrorState() = runTest {
    val presenter =
      SearchAndFilterPresenter(
        FakeContinentRepository(
          continentsAsFlow = { flowOf(Outcome.Error(DataError.Network, Origin.Network)) }
        )
      )

    presenter.test {
      val failed = awaitContinentsSettled().continentsState
      assertTrue(failed.status is LoadStatus.Failed)
      assertEquals(DataError.Network, failed.errorOrNull)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun togglingAContinentAddsThenRemovesIt() = runTest {
    val presenter = SearchAndFilterPresenter(FakeContinentRepository())

    presenter.test {
      val state = awaitItem()
      state.eventSink(SearchAndFilterScreen.Event.ContinentToggled(europe))
      // .toList(): selectedContinents is a SnapshotStateList, whose equals() is structural on
      // JVM/Android but identity-based on native and Kotlin/JS. Comparing it to a plain list
      // passed only by accident before this module was multiplatform.
      assertEquals(listOf(europe), state.selectedContinents.toList())

      state.eventSink(SearchAndFilterScreen.Event.ContinentToggled(europe))
      assertTrue(state.selectedContinents.isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * The whole point of the sub-circuit: whatever the user sets here has to reach the host, because
   * the host has no other way to see it.
   */
  @Test
  fun reportsTheFilterUpwardOnEveryChange() = runTest {
    val presenter = SearchAndFilterPresenter(FakeContinentRepository())
    // Captured before entering the block, where the Turbine receiver shadows the TestScope. The
    // report is sent from a LaunchedEffect, which only runs once the composition is pumped — so
    // each step has to let the scheduler drain before the event can be there to await.
    val scheduler = testScheduler

    presenter.test {
      val state = awaitItem()
      scheduler.advanceUntilIdle()
      // Reported once on first composition, so a host that has just been created learns the filter
      // it should already be querying with.
      assertEquals(
        SearchAndFilterScreen.OuterEvent.FilterChanged("", emptyList()),
        outerEvents.awaitEvent(),
      )

      state.eventSink(SearchAndFilterScreen.Event.SearchTextChanged("fr"))
      scheduler.advanceUntilIdle()
      assertEquals(
        SearchAndFilterScreen.OuterEvent.FilterChanged("fr", emptyList()),
        outerEvents.awaitEvent(),
      )

      state.eventSink(SearchAndFilterScreen.Event.ContinentToggled(europe))
      scheduler.advanceUntilIdle()
      assertEquals(
        SearchAndFilterScreen.OuterEvent.FilterChanged("fr", listOf(europe)),
        outerEvents.awaitEvent(),
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * Driven through the event rather than by mutating the
   * [androidx.compose.foundation.text.input.TextFieldState] — the only route open to a host that is
   * not a composition, such as the SwiftUI app.
   */
  @Test
  fun searchTextChangedEventUpdatesTheTextFieldState() = runTest {
    val presenter = SearchAndFilterPresenter(FakeContinentRepository())

    presenter.test {
      val state = awaitItem()
      state.eventSink(SearchAndFilterScreen.Event.SearchTextChanged("fr"))
      // The event is the sole writer, so the TextFieldState has to end up holding the same text —
      // otherwise a Compose host sharing this presenter would show a stale search box.
      assertEquals("fr", state.nameStartsWithText.text.toString())
      cancelAndIgnoreRemainingEvents()
    }
  }

  /** Menu open/closed is presenter state, so that it is assertable without rendering anything. */
  @Test
  fun dropdownExpandedChangedTogglesTheMenu() = runTest {
    val presenter = SearchAndFilterPresenter(FakeContinentRepository())

    presenter.test {
      assertTrue(!awaitItem().continentDropdownExpanded)

      awaitItem().eventSink(SearchAndFilterScreen.Event.DropdownExpandedChanged(true))
      assertTrue(awaitItem().continentDropdownExpanded)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun <T> data(value: T, origin: Origin = Origin.Network): Outcome<T> =
    Outcome.Data(value, origin)
}

/** Drains emissions until the continent content has settled (loaded or failed). */
private suspend fun ReceiveTurbine<SearchAndFilterScreen.State>.awaitContinentsSettled():
  SearchAndFilterScreen.State {
  while (true) {
    val state = awaitItem()
    if (state.continentsState.status !is LoadStatus.Loading) return state
  }
}

class FakeContinentRepository(
  private val continentsAsFlow: () -> Flow<Outcome<List<Continent>>> = { emptyFlow() }
) : ContinentRepository {
  override fun continentsAsFlow(): Flow<Outcome<List<Continent>>> = continentsAsFlow.invoke()
}
