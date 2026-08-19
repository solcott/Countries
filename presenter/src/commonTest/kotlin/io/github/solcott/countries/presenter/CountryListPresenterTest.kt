package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.countries.dataresult.DataError
import io.github.solcott.countries.dataresult.Origin
import io.github.solcott.countries.dataresult.Outcome
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.repository.ContinentRepository
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
  val continents = listOf(africa, europe, oceania)

  @Test
  fun loadsCountriesAndEmitsLoadedState() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(countriesAsFlow = { _, _ -> flowOf(data(listOf(canada))) })

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
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

    val continentRepository =
      FakeContinentRepository(continentsAsFlow = { flowOf(data(continents)) })

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      val initial = awaitFullySettled()
      assertEquals(countries, initial.countriesState.data)
      assertEquals(continents, initial.continentsState.data)

      initial.nameStartsWithText.setTextAndPlaceCursorAtEnd("c")
      assertEquals(listOf(canada), awaitCountriesSettled().data)

      initial.eventSink(CountryListScreen.Event.ToggleContinentSelection(europe))
      assertEquals(emptyList<Country>(), awaitCountriesSettled().data)

      initial.eventSink(CountryListScreen.Event.ToggleContinentSelection(europe))
      assertEquals(listOf(canada), awaitCountriesSettled().data)

      initial.nameStartsWithText.setTextAndPlaceCursorAtEnd("")
      assertEquals(countries, awaitCountriesSettled().data)
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * The same filtering as above, driven through [CountryListScreen.Event.SearchTextChanged] rather
   * than by mutating the [androidx.compose.foundation.text.input.TextFieldState] — the only route
   * open to a host that is not a composition, such as the SwiftUI app.
   */
  @Test
  fun searchTextChangedEventFiltersCountries() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(
        countriesAsFlow = { name, _ ->
          if (name.startsWith("c")) flowOf(data(listOf(canada))) else flowOf(data(countries))
        }
      )

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      val initial = awaitCountriesSettledState()
      assertEquals(countries, initial.countriesState.data)

      initial.eventSink(CountryListScreen.Event.SearchTextChanged("c"))
      assertEquals(listOf(canada), awaitCountriesSettled().data)
      // The event is the sole writer, so the TextFieldState has to end up holding the same text —
      // otherwise a Compose host sharing this presenter would show a stale search box.
      assertEquals("c", initial.nameStartsWithText.text.toString())

      initial.eventSink(CountryListScreen.Event.SearchTextChanged(""))
      assertEquals(countries, awaitCountriesSettled().data)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun clickingACountryNavigatesToDetail() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
      FakeCountryRepository(countriesAsFlow = { _, _ -> flowOf(data(listOf(canada))) })

    val continentRepository = FakeContinentRepository()
    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
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

    val continentRepository = FakeContinentRepository()
    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
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

    val continentRepository = FakeContinentRepository()
    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
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

    val continentRepository = FakeContinentRepository()
    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
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

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      assertTrue(awaitItem().countriesState.isLoading)

      val errorState = awaitCountriesSettled()
      assertTrue(errorState.status is LoadStatus.Failed)
      assertEquals(DataError.Network, errorState.errorOrNull)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun loadsContinentsAndEmitsLoadedState() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository = FakeCountryRepository()

    val continentRepository =
      FakeContinentRepository(continentsAsFlow = { flowOf(data(listOf(europe))) })

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      assertTrue(awaitItem().continentsState.isLoading)

      val loaded = awaitContinentsSettled()
      assertEquals(listOf(europe), loaded.data)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun selectingContinentUpdatesSelectedContinents() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository = FakeCountryRepository()

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      val state = awaitItem()
      state.eventSink(CountryListScreen.Event.ToggleContinentSelection(europe))
      // .toList(): selectedContinents is a SnapshotStateList, whose equals() is structural on
      // JVM/Android but identity-based on native and Kotlin/JS. Comparing it to a plain list
      // passed only by accident before this module was multiplatform.
      assertEquals(listOf(europe), state.selectedContinents.toList())
      state.eventSink(CountryListScreen.Event.ToggleContinentSelection(europe))
      assertTrue(state.selectedContinents.isEmpty())
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

private suspend fun ReceiveTurbine<CountryListScreen.State>.awaitContinentsSettled():
  ContentState<List<Continent>> {
  while (true) {
    val state = awaitItem()
    if (state.continentsState.status !is LoadStatus.Loading) return state.continentsState
  }
}

/** Drains until both the country and continent content have settled. */
private suspend fun ReceiveTurbine<CountryListScreen.State>.awaitFullySettled():
  CountryListScreen.State {
  while (true) {
    val state = awaitItem()
    if (
      state.countriesState.status !is LoadStatus.Loading &&
        state.continentsState.status !is LoadStatus.Loading
    ) {
      return state
    }
  }
}

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

class FakeContinentRepository(
  private val continentsAsFlow: () -> Flow<Outcome<List<Continent>>> = { emptyFlow() }
) : ContinentRepository {
  override fun continentsAsFlow(): Flow<Outcome<List<Continent>>> = continentsAsFlow.invoke()
}
