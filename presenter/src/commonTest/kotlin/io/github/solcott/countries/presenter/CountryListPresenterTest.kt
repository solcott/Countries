package io.github.solcott.countries.presenter

import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.countries.dataresult.DataError
import io.github.solcott.countries.dataresult.Origin
import io.github.solcott.countries.dataresult.Outcome
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.repository.CountryRepository
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.countries.uistate.LoadStatus
import io.github.solcott.countries.uistate.errorOrNull
import io.github.solcott.countries.uistate.isLoading
import kotlin.collections.emptyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * The list presenter no longer owns the search text or the continent selection — `SearchAndFilter`
 * does, and reports them through [CountryListScreen.Event.FilterChanged]. So every filtering test
 * here drives that event, which is exactly what `CountryListUi` and the Apple holder do with the
 * sub-circuit's outer event. `SearchAndFilterPresenterTest` covers the other side.
 */
class CountryListPresenterTest {

  private val canada =
    Country(
      code = "CA",
      name = "Canada",
      emoji = "🇨🇦",
      capital = "Ottawa",
      continentName = "North America",
    )
  val egypt =
    Country(
      code = "EG",
      emoji = "🇪🇬",
      capital = "Cairo",
      continentName = "Africa",
      name = "Egypt",
    )

  val angola =
    Country(
      code = "AO",
      emoji = "🇦🇴",
      capital = "Luanda",
      continentName = "Africa",
      name = "Angola",
    )

  val fiji =
    Country(
      code = "FJ",
      emoji = "🇫🇯",
      capital = "Suva",
      continentName = "Oceania",
      name = "Fiji",
    )

  val africa =
    Continent(
      code = "AF",
      name = "Africa",
    )

  val oceania = Continent(code = "OC", name = "Oceania")

  val europe = Continent("EU", "Europe")

  val countries = listOf(canada, egypt, fiji)

  @Test
  fun loadsCountriesAndEmitsLoadedState() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(countriesAsFlow = { _, _ -> flowOf(data(listOf(canada))) })

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      assertTrue(awaitItem().countriesState.isLoading)

      val loaded = awaitCountriesSettled()
      assertEquals(listOf(canada), loaded.data)
      assertEquals(Origin.Network, loaded.origin)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun filtersCountriesByNameAndContinent() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(
        countriesAsFlow = { name, cs ->
          if (name.startsWith("c") && cs.isEmpty()) {
            flowOf(data(listOf(canada)))
          } else if (name.startsWith("c") && cs.contains(europe.code)) {
            flowOf(data(emptyList()))
          } else if (name.startsWith("e") && cs.contains(africa.code)) {
            flowOf(data(listOf(egypt)))
          } else {
            flowOf(data(countries))
          }
        }
      )

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      val initial = awaitCountriesSettledState()
      assertEquals(countries, initial.countriesState.data)

      initial.eventSink(CountryListScreen.Event.FilterChanged("c", emptyList()))
      assertEquals(listOf(canada), awaitCountriesSettled().data)

      initial.eventSink(CountryListScreen.Event.FilterChanged("c", listOf(europe)))
      assertEquals(emptyList<Country>(), awaitCountriesSettled().data)

      initial.eventSink(CountryListScreen.Event.FilterChanged("c", emptyList()))
      assertEquals(listOf(canada), awaitCountriesSettled().data)

      initial.eventSink(CountryListScreen.Event.FilterChanged("", emptyList()))
      assertEquals(countries, awaitCountriesSettled().data)
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * The sub-circuit re-reports its filter whenever it is composed, and a fast edit that ends back
   * where it started collapses to the value already in flight once the debounce has had its say.
   * Neither may restart an identical request.
   */
  @Test
  fun repeatingTheSameFilterDoesNotRequery() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    var queries = 0
    val repository =
      FakeCountryRepository(
        countriesAsFlow = { _, _ ->
          queries++
          flowOf(data(countries))
        }
      )

    val scheduler = testScheduler
    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      val initial = awaitCountriesSettledState()
      assertEquals(1, queries)

      // Typed and then undone inside the debounce window, so what finally reaches the query is the
      // filter that is already loaded.
      initial.eventSink(CountryListScreen.Event.FilterChanged("c", emptyList()))
      initial.eventSink(CountryListScreen.Event.FilterChanged("", emptyList()))
      scheduler.advanceUntilIdle()

      assertEquals(1, queries)
      // And with no request, no state change either — the list never blinks through `reloading`.
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun clickingACountryNavigatesToDetail() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(countriesAsFlow = { _, _ -> flowOf(data(listOf(canada))) })

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      val loaded = awaitCountriesSettledState()

      loaded.eventSink(CountryListScreen.Event.CountryClicked("CA"))

      assertEquals(CountryDetailScreen("CA"), navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * The two-pane case: the list stays on screen beside the detail, so a second click arrives while
   * a country is already open. It has to replace that country rather than stack a second detail on
   * top of it.
   */
  @Test
  fun clickingACountryWhileADetailIsOpenReplacesIt() = runTest {
    val navigator = FakeNavigator(CountryListScreen, CountryDetailScreen("CA"))
    val repository =
      FakeCountryRepository(countriesAsFlow = { _, _ -> flowOf(data(listOf(canada, egypt))) })

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      val loaded = awaitCountriesSettledState()
      assertEquals("CA", loaded.selectedCountryCode)

      loaded.eventSink(CountryListScreen.Event.CountryClicked("EG"))

      assertEquals(CountryDetailScreen("CA"), navigator.awaitPop().poppedScreen)
      assertEquals(CountryDetailScreen("EG"), navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * Re-tapping the row already showing is not a navigation — it would only churn the back stack.
   */
  @Test
  fun clickingTheAlreadySelectedCountryDoesNothing() = runTest {
    val navigator = FakeNavigator(CountryListScreen, CountryDetailScreen("CA"))
    val repository =
      FakeCountryRepository(countriesAsFlow = { _, _ -> flowOf(data(listOf(canada))) })

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      val loaded = awaitCountriesSettledState()

      loaded.eventSink(CountryListScreen.Event.CountryClicked("CA"))

      navigator.assertGoToIsEmpty()
      navigator.assertPopIsEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  /** Nothing open: the list has no row to mark. */
  @Test
  fun selectedCountryCodeIsNullWithNoDetailOpen() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(countriesAsFlow = { _, _ -> flowOf(data(listOf(canada))) })

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      assertEquals(null, awaitCountriesSettledState().selectedCountryCode)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun surfacesCountryFailuresAsErrorState() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(
        countriesAsFlow = { _, _ -> flowOf(Outcome.Error(DataError.Network, Origin.Network)) }
      )

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      assertTrue(awaitItem().countriesState.isLoading)

      val errorState = awaitCountriesSettled()
      assertTrue(errorState.status is LoadStatus.Failed)
      assertEquals(DataError.Network, errorState.errorOrNull)
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * Retry re-runs the query with whatever filter was last reported. Tested from a failure, because
   * that is the only state the retry affordance is offered in.
   */
  @Test
  fun retryReloadsTheCountriesAfterAFailure() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    var queries = 0
    val repository =
      FakeCountryRepository(
        countriesAsFlow = { _, _ ->
          queries++
          if (queries == 1) {
            flowOf(Outcome.Error(DataError.Network, Origin.Network))
          } else {
            flowOf(data(countries))
          }
        }
      )

    presenterTestOf({ CountryListPresenter(navigator, repository) }) {
      val failed = awaitCountriesSettledState()
      assertTrue(failed.countriesState.status is LoadStatus.Failed)

      failed.eventSink(CountryListScreen.Event.Retry)

      assertEquals(countries, awaitCountriesSettled().data)
      assertEquals(2, queries)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun <T> data(value: T, origin: Origin = Origin.Network): Outcome<T> =
    Outcome.Data(value, origin)
}

/**
 * Drains emissions until the country content has settled (loaded or failed), returning that state.
 */
private suspend fun ReceiveTurbine<CountryListScreen.State>.awaitCountriesSettledState():
  CountryListScreen.State {
  while (true) {
    val state = awaitItem()
    if (state.countriesState.status !is LoadStatus.Loading) return state
  }
}

private suspend fun ReceiveTurbine<CountryListScreen.State>.awaitCountriesSettled():
  ContentState<List<Country>> = awaitCountriesSettledState().countriesState

class FakeCountryRepository(
  private val countriesAsFlow:
    (nameStartsWith: String, continentCodes: List<String>) -> Flow<Outcome<List<Country>>> =
    { _, _ ->
      emptyFlow()
    },
  private val countryAsFlow: (code: String) -> Flow<Outcome<CountryDetail?>> = { emptyFlow() },
) : CountryRepository {
  override fun countriesAsFlow(
    nameStartsWith: String,
    continentCodes: List<String>,
  ): Flow<Outcome<List<Country>>> = countriesAsFlow.invoke(nameStartsWith, continentCodes)

  override fun countryAsFlow(code: String): Flow<Outcome<CountryDetail?>> =
    countryAsFlow.invoke(code)
}
