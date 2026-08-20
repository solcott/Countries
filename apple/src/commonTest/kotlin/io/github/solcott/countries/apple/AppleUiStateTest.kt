package io.github.solcott.countries.apple

import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins [CountryListUiState]'s and [CountryDetailUiState]'s value equality.
 *
 * Molecule builds one of these per recomposition, and the SwiftUI app re-renders the whole view
 * whenever `StateFlow` hands it a new value. Without equality every frame is a distinct instance
 * and nothing can be conflated — which is invisible from the app until the list is janky.
 *
 * Both are `data class`es, so the data fields take care of themselves and an added field is
 * included automatically. What is fragile is the other half: [EventSink] is what keeps the sinks,
 * fresh lambdas on every frame, from making every frame unequal. Unwrapping one back to a raw
 * lambda would undo all of this silently, which is what these tests are here to catch.
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

  private fun listState(
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
      eventSink = EventSink {},
      headerEventSink = EventSink {},
    )

  @Test
  fun listFramesCarryingTheSameDataAreEqualDespiteFreshEventSinks() {
    assertEquals(listState(), listState())
    assertEquals(listState().hashCode(), listState().hashCode())
  }

  @Test
  fun aChangeInAnyListDataFieldBreaksEquality() {
    assertNotEquals(listState(), listState(countries = emptyList()))
    assertNotEquals(listState(), listState(selectedContinents = emptyList()))
    assertNotEquals(listState(), listState(searchText = "c"))
    assertNotEquals(listState(), listState(countriesStatus = LoadStatus.Loading))
  }

  private val canadaDetail =
    CountryDetail(
      code = "CA",
      name = "Canada",
      nativeName = "Canada",
      emoji = "🇨🇦",
      capital = "Ottawa",
      currency = "CAD",
      phone = "1",
      continentName = "North America",
      languages = emptyList(),
    )

  private fun detailState(
    country: CountryDetail? = canadaDetail,
    status: LoadStatus = LoadStatus.Idle,
  ) = CountryDetailUiState(country = country, status = status, eventSink = EventSink {})

  @Test
  fun detailFramesCarryingTheSameDataAreEqualDespiteFreshEventSinks() {
    assertEquals(detailState(), detailState())
    assertEquals(detailState().hashCode(), detailState().hashCode())
  }

  @Test
  fun aChangeInAnyDetailDataFieldBreaksEquality() {
    assertNotEquals(detailState(), detailState(country = null))
    assertNotEquals(detailState(), detailState(status = LoadStatus.Loading))
  }
}
