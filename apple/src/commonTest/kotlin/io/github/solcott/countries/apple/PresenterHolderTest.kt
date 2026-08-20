package io.github.solcott.countries.apple

import app.cash.turbine.test
import io.github.solcott.countries.dataresult.Origin
import io.github.solcott.countries.dataresult.Outcome
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository
import io.github.solcott.countries.uistate.LoadStatus
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
 *
 * The filter tests carry more weight than they look. `SearchAndFilterScreen` is a sub-circuit, and
 * a sub-presenter is normally run by `SubCircuitContent` — a Compose UI composable that does not
 * exist on this platform. [CountryListPresenterHolder] composes it by hand instead, so these are
 * what stand between the SwiftUI app and a search field that silently does nothing.
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

  private val europe = Continent(code = "EU", name = "Europe")

  @Test
  fun countryListHolderEmitsStateFromTheComposablePresenter() = runTest {
    val holder =
      CountryListPresenterHolder(
        navigator = SwiftNavigator(onShowCountry = {}, onPop = {}),
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

  /**
   * The sub-circuit's continent load has to reach the state Swift reads, or the filter menu is
   * never offered.
   */
  @Test
  fun countryListHolderSurfacesContinentsFromTheSubCircuit() = runTest {
    val holder =
      CountryListPresenterHolder(
        navigator = SwiftNavigator(onShowCountry = {}, onPop = {}),
        countryRepository = FakeCountryRepository(),
        continentRepository =
          FakeContinentRepository(
            continentsAsFlow = { flowOf(Outcome.Data(listOf(europe), Origin.Network)) }
          ),
        scope = backgroundScope,
      )

    holder.state.test {
      var state = awaitItem()
      while (state.continentsStatus is LoadStatus.Loading) state = awaitItem()

      assertEquals(listOf(europe), state.continents)
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * `search` lands on the sub-circuit, whose report has to travel back into the list presenter's
   * query. Two presenters and one round trip, all inside a single Molecule frame.
   */
  @Test
  fun searchReachesTheCountryQuery() = runTest {
    val holder =
      CountryListPresenterHolder(
        navigator = SwiftNavigator(onShowCountry = {}, onPop = {}),
        countryRepository =
          FakeCountryRepository(
            countriesAsFlow = { name, _ ->
              val matches = if (name.startsWith("c")) listOf(canada) else emptyList()
              flowOf(Outcome.Data(matches, Origin.Network))
            }
          ),
        continentRepository = FakeContinentRepository(),
        scope = backgroundScope,
      )

    holder.state.test {
      var state = awaitItem()
      while (state.countriesStatus is LoadStatus.Loading) state = awaitItem()
      assertEquals(emptyList(), state.countries)

      holder.search("c")

      while (state.countries.isEmpty()) state = awaitItem()
      assertEquals(listOf(canada), state.countries)
      // SwiftUI owns its own search field, but the echoed text still has to be right — the macOS
      // ⌘F command and any programmatic set read it back.
      assertEquals("c", state.searchText)
      cancelAndIgnoreRemainingEvents()
    }
  }

  /** The same round trip for the continent menu. */
  @Test
  fun togglingAContinentReachesTheCountryQuery() = runTest {
    val holder =
      CountryListPresenterHolder(
        navigator = SwiftNavigator(onShowCountry = {}, onPop = {}),
        countryRepository =
          FakeCountryRepository(
            countriesAsFlow = { _, codes ->
              val matches = if (codes.contains(europe.code)) listOf(canada) else emptyList()
              flowOf(Outcome.Data(matches, Origin.Network))
            }
          ),
        continentRepository =
          FakeContinentRepository(
            continentsAsFlow = { flowOf(Outcome.Data(listOf(europe), Origin.Network)) }
          ),
        scope = backgroundScope,
      )

    holder.state.test {
      var state = awaitItem()
      while (state.countriesStatus is LoadStatus.Loading) state = awaitItem()
      assertEquals(emptyList(), state.countries)

      holder.toggleContinent(europe)

      while (state.countries.isEmpty()) state = awaitItem()
      assertEquals(listOf(canada), state.countries)
      // .toList(): this is the sub-circuit's SnapshotStateList, and its equals() is identity-based
      // on native — the assertion passes on JVM and fails here without the copy.
      assertEquals(listOf(europe), state.selectedContinents.toList())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun countryListHolderRoutesEventsBackIntoThePresenter() = runTest {
    var navigatedTo: String? = null
    val holder =
      CountryListPresenterHolder(
        navigator = SwiftNavigator(onShowCountry = { navigatedTo = it }, onPop = {}),
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
