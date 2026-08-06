package io.github.solcott.countries.presenter

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.DataError
import io.github.solcott.countries.model.Origin
import io.github.solcott.countries.model.Outcome
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository
import kotlin.collections.emptyList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
  fun `loads countries and emits loaded state`() = runTest {
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
  fun `filter countries by name and continent`() = runTest {
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

  @Test
  fun `clicking a country navigates to detail`() = runTest {
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

  @Test
  fun `surfaces country failures as error state`() = runTest {
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
  fun `loads continents and emits loaded state`() = runTest {
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
  fun `selecting continent updates selected continents`() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository = FakeCountryRepository()

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      val state = awaitItem()
      state.eventSink(CountryListScreen.Event.ToggleContinentSelection(europe))
      assertEquals(listOf(europe), state.selectedContinents)
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
