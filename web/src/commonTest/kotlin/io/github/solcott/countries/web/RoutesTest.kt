package io.github.solcott.countries.web

import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutesTest {

  @Test
  fun theListScreenIsTheRootRoute() {
    assertEquals(LIST_ROUTE, CountryListScreen.toRoute())
  }

  @Test
  fun aDetailScreenCarriesItsCountryCode() {
    assertEquals("#/country/AD", CountryDetailScreen("AD").toRoute())
  }

  /** A bare URL with no fragment at all is the list — this is what a plain `/` load looks like. */
  @Test
  fun anEmptyHashIsTheList() {
    assertEquals(CountryListScreen, "".toScreen())
  }

  @Test
  fun aBareHashIsTheList() {
    assertEquals(CountryListScreen, "#".toScreen())
  }

  @Test
  fun theRootRouteIsTheList() {
    assertEquals(CountryListScreen, LIST_ROUTE.toScreen())
  }

  @Test
  fun aCountryRouteIsTheDetailScreen() {
    assertEquals(CountryDetailScreen("AD"), "#/country/AD".toScreen())
  }

  @Test
  fun anUnrecognisedRouteResolvesToNothing() {
    assertNull("#/nonsense".toScreen())
  }

  /** A hand-truncated URL should not become a detail screen with a blank code. */
  @Test
  fun aCountryRouteWithNoCodeResolvesToNothing() {
    assertNull("#/country/".toScreen())
  }

  @Test
  fun theRootRouteOpensOnTheListAlone() {
    assertEquals(listOf(CountryListScreen), LIST_ROUTE.toInitialScreens())
  }

  /** Root-first, so browser back from a shared link goes to the list rather than out of the app. */
  @Test
  fun aDeepLinkOpensWithTheListUnderneath() {
    assertEquals(
      listOf(CountryListScreen, CountryDetailScreen("AD")),
      "#/country/AD".toInitialScreens(),
    )
  }

  @Test
  fun anUnrecognisedRouteFallsBackToTheList() {
    assertEquals(listOf(CountryListScreen), "#/nonsense".toInitialScreens())
  }
}
