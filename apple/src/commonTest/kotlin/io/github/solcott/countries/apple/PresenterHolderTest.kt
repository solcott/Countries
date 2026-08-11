package io.github.solcott.countries.apple

import app.cash.turbine.test
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Origin
import io.github.solcott.countries.model.Outcome
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.LoadStatus
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Proves the Molecule bridge actually turns a `@Composable` presenter into an observable
 * [kotlinx.coroutines.flow.StateFlow] on the Apple targets.
 *
 * The presenters themselves are covered in `:presenter`; what is unproven here is the seam — that
 * Molecule recomposes at all under this Kotlin and Compose compiler pairing, and that the state
 * reaches a subscriber. Everything Swift does rests on that, so it is worth one test rather than
 * discovering it from an empty list in a simulator.
 */
class PresenterHolderTest {

  private val canada =
    Country(
      code = "CA",
      name = "Canada",
      emoji = "🇨🇦",
      capital = "Ottawa",
      continentName = "North America",
    )

  @Test
  fun countryListHolderEmitsStateFromTheComposablePresenter() = runTest {
    val holder =
      CountryListPresenterHolder(
        navigator = SwiftNavigator(CountryListScreen, onGoTo = {}, onPop = {}),
        countryRepository =
          FakeCountryRepository(
            countriesAsFlow = { _, _ -> flowOf(Outcome.Data(listOf(canada), Origin.Network)) }
          ),
        continentRepository = FakeContinentRepository(),
        scope = backgroundScope,
      )

    holder.state.test {
      var state = awaitItem()
      while (state.countriesStatus is LoadStatus.Loading) state = awaitItem()

      assertEquals(listOf(canada), state.countries)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun countryListHolderRoutesEventsBackIntoThePresenter() = runTest {
    var navigatedTo: String? = null
    val holder =
      CountryListPresenterHolder(
        navigator =
          SwiftNavigator(
            CountryListScreen,
            onGoTo = { navigatedTo = (it as? CountryDetailScreen)?.code },
            onPop = {},
          ),
        countryRepository =
          FakeCountryRepository(
            countriesAsFlow = { _, _ -> flowOf(Outcome.Data(listOf(canada), Origin.Network)) }
          ),
        continentRepository = FakeContinentRepository(),
        scope = backgroundScope,
      )

    holder.state.test {
      var state = awaitItem()
      while (state.countriesStatus is LoadStatus.Loading) state = awaitItem()

      state.eventSink(CountryListScreen.Event.CountryClicked("CA"))

      assertEquals("CA", navigatedTo)
      cancelAndIgnoreRemainingEvents()
    }
  }
}

private class FakeCountryRepository(
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

private class FakeContinentRepository(
  private val continentsAsFlow: () -> Flow<Outcome<List<Continent>>> = { emptyFlow() }
) : ContinentRepository {
  override fun continentsAsFlow(): Flow<Outcome<List<Continent>>> = continentsAsFlow.invoke()
}
