package io.github.solcott.countries.presenter

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Response
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository
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

  val europe = Continent("EU", "Europe")

  @Test
  fun `loads countries and emits loaded state`() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
        FakeCountryRepository(
            countriesAsFlow = { _, _ -> flowOf(Response.Loading(), Response.Data(listOf(canada))) }
        )

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      val loadingState = awaitItem() // Loading
      assertTrue(loadingState.countriesState.loading)

      val loaded = awaitItem()
      assertEquals(listOf(canada), loaded.countriesState.data)
    }
  }

  @Test
  fun `clicking a country navigates to detail`() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
        FakeCountryRepository(
            countriesAsFlow = { _, _ -> flowOf(Response.Loading(), Response.Data(listOf(canada))) }
        )

    val continentRepository = FakeContinentRepository()
    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      awaitItem() // Loading
      val loaded = awaitItem()

      loaded.eventSink(CountryListScreen.Event.CountryClicked("CA"))

      assertEquals(CountryDetailScreen("CA"), navigator.awaitNextScreen())
    }
  }

  @Test
  fun `surfaces country failures as error state`() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository =
        FakeCountryRepository(
            countriesAsFlow = { _, _ -> flowOf(Response.Loading(), Response.Error("network down")) }
        )

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      val loadingState = awaitItem() // Loading
      assertTrue(loadingState.countriesState.loading)
      val errorState = awaitItem()
      assertTrue(errorState.countriesState.error)
      assertEquals("network down", errorState.countriesState.errorMessage)
    }
  }

  @Test
  fun `loads continents and emits loaded state`() {

    runTest {
      val navigator = FakeNavigator(CountryListScreen)
      val repository = FakeCountryRepository()

      val continentRepository =
          FakeContinentRepository(
              continentsAsFlow = {
                flowOf(Response.Loading(), Response.Data(listOf(europe)))
              }
          )

      presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
        val loadingState = awaitItem() // Loading
        println(loadingState)
        assertTrue(loadingState.continentsState.loading)

        val loaded = awaitItem()
        assertEquals(listOf(europe), loaded.continentsState.data)
      }
    }
  }

  @Test
  fun `selecting continent updates selected continents`() = runTest {
    val navigator = FakeNavigator(CountryListScreen)
    val repository = FakeCountryRepository()

    val continentRepository = FakeContinentRepository()

    presenterTestOf({ CountryListPresenter(navigator, repository, continentRepository) }) {
      val state = awaitItem() // Loading
      state.eventSink(CountryListScreen.Event.ContinentSelected(europe))
      assertEquals(listOf(europe), state.selectedContinents)
      state.eventSink(CountryListScreen.Event.ContinentSelected(europe))
      assertTrue(state.selectedContinents.isEmpty())
    }
  }
}

class FakeCountryRepository(
    private val countriesAsFlow:
        (
            nameStartsWith: String,
            continentCodes: List<String>,
        ) -> Flow<Response<List<Country>>> =
        { _, _ ->
          emptyFlow()
        },
    private val countryAsFlow: (code: String) -> Flow<Response<CountryDetail?>> = {
      emptyFlow()
    },
) : CountryRepository {
  override fun countriesAsFlow(
      nameStartsWith: String,
      continentCodes: List<String>,
  ): Flow<Response<List<Country>>> = countriesAsFlow.invoke(nameStartsWith, continentCodes)

  override fun countryAsFlow(code: String): Flow<Response<CountryDetail?>> =
      countryAsFlow.invoke(code)
}

class FakeContinentRepository(
    private val continentsAsFlow: () -> Flow<Response<List<Continent>>> = {
      emptyFlow()
    },
) : ContinentRepository {
  override fun continentsAsFlow(): Flow<Response<List<Continent>>> = continentsAsFlow.invoke()
}
