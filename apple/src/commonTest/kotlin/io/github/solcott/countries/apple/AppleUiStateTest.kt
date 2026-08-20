package io.github.solcott.countries.apple

import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins [CountryListUiState]'s value equality.
 *
 * Molecule builds one of these per recomposition, and the SwiftUI app re-renders the whole list
 * whenever `StateFlow` hands it a new value. Without equality every frame is a distinct instance
 * and nothing can be conflated — which is invisible from the app until the list is janky, and
 * exactly the kind of thing a later `data class` refactor or an added field would undo silently.
 */
class AppleUiStateTest {

  private val canada =
    Country(
      code = "CA",
      name = "Canada",
      emoji = "🇨🇦",
      capital = "Ottawa",
      continentName = "North America",
    )
  private val europe = Continent(code = "EU", name = "Europe")

  private fun state(
    countries: List<Country> = listOf(canada),
    selectedContinents: List<Continent> = listOf(europe),
    searchText: String = "ca",
    countriesStatus: LoadStatus = LoadStatus.Idle,
  ) =
    CountryListUiState(
      countries = countries,
      continents = listOf(europe),
      selectedContinents = selectedContinents,
      searchText = searchText,
      countriesStatus = countriesStatus,
      continentsStatus = LoadStatus.Idle,
      // Fresh lambdas, as they are on every real frame. They must not affect equality.
      eventSink = {},
      headerEventSink = {},
    )

  @Test
  fun framesCarryingTheSameDataAreEqualDespiteFreshEventSinks() {
    assertEquals(state(), state())
    assertEquals(state().hashCode(), state().hashCode())
  }

  @Test
  fun aChangeInAnyDataFieldBreaksEquality() {
    assertNotEquals(state(), state(countries = emptyList()))
    assertNotEquals(state(), state(selectedContinents = emptyList()))
    assertNotEquals(state(), state(searchText = "c"))
    assertNotEquals(state(), state(countriesStatus = LoadStatus.Loading))
  }
}
