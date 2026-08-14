package io.github.solcott.countries.apple

import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwiftNavigatorTest {

  @Test
  fun goToForwardsTheCountryCodeAndReportsSuccess() {
    val seen = mutableListOf<String>()
    val navigator = SwiftNavigator(onShowCountry = { seen += it }, onPop = {})

    val handled = navigator.goTo(CountryDetailScreen("CA"))

    assertTrue(handled)
    // A code, not a Screen: Circuit's types stay on this side of the bridge.
    assertEquals(listOf("CA"), seen.toList())
  }

  @Test
  fun goToIgnoresScreensThatAreNotACountry() {
    val seen = mutableListOf<String>()
    val navigator = SwiftNavigator(onShowCountry = { seen += it }, onPop = {})

    assertTrue(navigator.goTo(CountryListScreen))

    // The list is the root and is never navigated *to*. Reported as handled anyway, because
    // returning false would tell Circuit the navigation failed when nothing was asked for.
    assertEquals(emptyList(), seen.toList())
  }

  @Test
  fun goToDoesNotChangeTheMirrorUntilSwiftSyncsBack() {
    val navigator = SwiftNavigator(onShowCountry = {}, onPop = {})

    navigator.goTo(CountryDetailScreen("CA"))

    // SwiftUI owns the stack: until it says otherwise, nothing has moved.
    assertEquals(listOf<Screen>(CountryListScreen), navigator.peekBackStack())

    navigator.syncFromSwift("CA")

    assertEquals(CountryDetailScreen("CA"), navigator.peek())
  }

  @Test
  fun popForwardsAndReturnsTheTopScreenWhenNotAtRoot() {
    var pops = 0
    val navigator = SwiftNavigator(onShowCountry = {}, onPop = { pops++ })
    navigator.syncFromSwift("CA")

    val popped = navigator.pop()

    assertEquals(CountryDetailScreen("CA"), popped)
    assertEquals(1, pops)
  }

  @Test
  fun popAtRootIsIgnored() {
    var pops = 0
    val navigator = SwiftNavigator(onShowCountry = {}, onPop = { pops++ })

    assertNull(navigator.pop())
    assertEquals(0, pops)
  }

  @Test
  fun syncFromSwiftWithNoSelectionFallsBackToTheRoot() {
    val navigator = SwiftNavigator(onShowCountry = {}, onPop = {})
    navigator.syncFromSwift("CA")

    navigator.syncFromSwift(null)

    // peek() returning null would read as "no screens at all", which is never true of a running
    // app.
    assertEquals(CountryListScreen, navigator.peek())
  }
}
